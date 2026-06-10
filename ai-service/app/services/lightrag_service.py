"""LightRAG 服务封装。

每个知识库（KB）对应一个 LightRAG workspace（命名 `kb_{kbId}`），KV / 向量 / 图 /
doc-status 四类存储统一落 PostgreSQL + Apache AGE。本模块负责：

- 注入 DeepSeek（OpenAI 兼容）作为 `llm_model_func`；
- 注入本地 bge-m3 作为 `embedding_func`（进程内单例，GPU 优先 / CPU 降级）；
- 按 workspace 复用 LightRAG 实例，避免重复初始化存储连接。

LightRAG 的 PG 后端通过 `POSTGRES_*` 环境变量读取连接信息，这里从配置的
`postgres_age_dsn` 解析后注入（见 `_ensure_pg_env`）。
"""
import asyncio
import os
from urllib.parse import unquote, urlparse

import numpy as np

from lightrag import LightRAG, QueryParam
from lightrag.llm.openai import openai_complete_if_cache
from lightrag.utils import wrap_embedding_func_with_attrs

from app.core.config import settings

# bge-m3 单进程单例 + 懒加载锁
_embed_model = None
_embed_lock = asyncio.Lock()
# encode 串行锁：torch/CUDA 模型的前向不是线程安全的，LightRAG 会并发调用
# embedding_func（多个 worker），必须串行化同一模型对象的 encode，否则段错误。
_encode_lock = asyncio.Lock()

# workspace -> 已初始化的 LightRAG 实例
_rag_instances: dict[str, LightRAG] = {}
_rag_lock = asyncio.Lock()


def _ensure_pg_env() -> None:
    """把 `postgres_age_dsn` 解析为 LightRAG PG 后端所需的 POSTGRES_* 环境变量。

    使用 setdefault：若部署环境（如 docker-compose）已显式设置某项，则尊重其值。
    """
    if os.environ.get("_ASUKA_PG_ENV_SET"):
        return
    p = urlparse(settings.postgres_age_dsn)
    os.environ.setdefault("POSTGRES_HOST", p.hostname or "localhost")
    os.environ.setdefault("POSTGRES_PORT", str(p.port or 5432))
    os.environ.setdefault("POSTGRES_USER", unquote(p.username) if p.username else "postgres")
    if p.password:
        os.environ.setdefault("POSTGRES_PASSWORD", unquote(p.password))
    os.environ.setdefault("POSTGRES_DATABASE", (p.path or "/postgres").lstrip("/") or "postgres")
    os.environ["_ASUKA_PG_ENV_SET"] = "1"


async def _get_embed_model():
    """懒加载 bge-m3（BGEM3FlagModel），进程内复用。GPU 可用时启用 fp16。"""
    global _embed_model
    if _embed_model is not None:
        return _embed_model
    async with _embed_lock:
        if _embed_model is None:
            import torch
            from FlagEmbedding import BGEM3FlagModel

            use_fp16 = torch.cuda.is_available()
            _embed_model = BGEM3FlagModel(settings.embed_model, use_fp16=use_fp16)
    return _embed_model


@wrap_embedding_func_with_attrs(embedding_dim=settings.embed_dim, max_token_size=8192)
async def _bge_m3_embed(texts: list[str]) -> np.ndarray:
    """bge-m3 稠密向量。encode 为同步密集计算，丢线程池避免阻塞事件循环。"""
    model = await _get_embed_model()
    loop = asyncio.get_running_loop()
    # 串行化 GPU 前向，避免多线程并发 encode 同一模型导致段错误
    async with _encode_lock:
        out = await loop.run_in_executor(
            None, lambda: model.encode(texts, batch_size=12, max_length=8192)
        )
    return np.asarray(out["dense_vecs"], dtype=np.float32)


async def _deepseek_complete(
    prompt,
    system_prompt=None,
    history_messages=None,
    keyword_extraction=False,
    entity_extraction=False,
    **kwargs,
) -> str:
    """DeepSeek（OpenAI 兼容）补全。内部 kwargs（如 hashing_kv）由下游适配器处理。"""
    entity_extraction = kwargs.pop("entity_extraction", entity_extraction)
    return await openai_complete_if_cache(
        settings.deepseek_chat_model,
        prompt,
        system_prompt=system_prompt,
        history_messages=history_messages or [],
        keyword_extraction=keyword_extraction,
        entity_extraction=entity_extraction,
        base_url=settings.deepseek_base_url,
        api_key=settings.deepseek_api_key,
        **kwargs,
    )


def workspace_of(kb_id) -> str:
    """KB id → LightRAG workspace 名（`kb_{id}`）。"""
    return f"{settings.lightrag_workspace_prefix}{kb_id}"


async def get_lightrag(kb_id) -> LightRAG:
    """获取（或初始化并缓存）某 KB 对应的 LightRAG 实例。

    同一 workspace 复用单实例；首次创建时完成存储后端初始化。
    """
    workspace = workspace_of(kb_id)
    inst = _rag_instances.get(workspace)
    if inst is not None:
        return inst
    async with _rag_lock:
        inst = _rag_instances.get(workspace)
        if inst is None:
            inst = await _create_lightrag(workspace)
            _rag_instances[workspace] = inst
    return inst


async def _create_lightrag(workspace: str) -> LightRAG:
    """构造并初始化一个 PG+AGE 后端的 LightRAG 实例。"""
    _ensure_pg_env()
    os.makedirs(settings.lightrag_working_dir, exist_ok=True)
    rag = LightRAG(
        working_dir=settings.lightrag_working_dir,
        workspace=workspace,
        llm_model_name=settings.deepseek_chat_model,
        llm_model_func=_deepseek_complete,
        embedding_func=_bge_m3_embed,
        # 本地单 GPU 串行 encode（见 _encode_lock），无需并发 embedding worker
        embedding_func_max_async=1,
        # 四类存储全部落 PostgreSQL（图走 AGE）
        kv_storage="PGKVStorage",
        vector_storage="PGVectorStorage",
        graph_storage="PGGraphStorage",
        doc_status_storage="PGDocStatusStorage",
    )
    # 必须：初始化存储后端（同时自动初始化 pipeline_status）
    await rag.initialize_storages()
    return rag


async def aquery(kb_id, question: str, mode: str = "mix") -> str:
    """对某 KB 整库问答（一次性回答）。Agent loop 在 P5 实现，这里是骨架最小查询。"""
    rag = await get_lightrag(kb_id)
    return await rag.aquery(question, param=QueryParam(mode=mode))


async def ainsert(kb_id, text: str, ids=None, file_paths=None) -> None:
    """把一段文本增量索引进某 KB 的 workspace。"""
    rag = await get_lightrag(kb_id)
    await rag.ainsert(text, ids=ids, file_paths=file_paths)


async def close_all() -> None:
    """释放所有已缓存的 LightRAG 实例的存储连接（服务关闭时调用）。"""
    async with _rag_lock:
        for inst in _rag_instances.values():
            await inst.finalize_storages()
        _rag_instances.clear()
