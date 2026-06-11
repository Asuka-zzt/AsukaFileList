"""Agent loop：知识库问答（整库）。

流程：查询分解（DeepSeek）→ 对每个子问题用 LightRAG `aquery_data(mode="mix")` 取原始
检索结果 → 充分性评估 → 不足且未达上限则改写再检索 → 聚合证据与引用 → 流式生成答案。

`agent_max_iters` 控制检索-评估迭代上限，`agent_timeout_s` 控制整体超时。结果以 SSE
事件流返回：status / token / citations / done / error。

单文档过滤（按 docId）在 P6 实现，这里 doc_id 仅透传、不过滤。
"""
import asyncio
import json

from lightrag import QueryParam
from lightrag.llm.openai import openai_complete_if_cache

from app.core.config import settings
from app.services import lightrag_service


def _sse(event: dict) -> str:
    """打包一个 SSE data 事件。"""
    return "data: " + json.dumps(event, ensure_ascii=False) + "\n\n"


async def _llm_json(prompt: str, system: str) -> dict:
    """调用 DeepSeek 并要求返回 JSON 对象，解析失败返回空 dict。"""
    text = await openai_complete_if_cache(
        settings.deepseek_chat_model, prompt, system_prompt=system,
        base_url=settings.deepseek_base_url, api_key=settings.deepseek_api_key,
        response_format={"type": "json_object"})
    try:
        return json.loads(text)
    except (json.JSONDecodeError, TypeError):
        return {}


async def _decompose(question: str) -> list[str]:
    """把复杂问题拆成 1~N 个检索子问题；简单问题原样返回。"""
    system = ("你是检索规划助手。把用户问题拆解为用于知识库检索的子问题，"
              "简单问题不必拆分。只输出 JSON：{\"subqueries\":[\"...\"]}，最多 4 个。")
    data = await _llm_json(question, system)
    subs = [s for s in data.get("subqueries", []) if isinstance(s, str) and s.strip()]
    return subs[:4] or [question]


async def _grade(question: str, context: str) -> dict:
    """评估已检索证据是否足以回答；不足时给出改写后的检索词。"""
    system = ("判断给定证据能否充分回答问题。只输出 JSON："
              "{\"sufficient\":true/false,\"refine\":\"补充检索词或空\"}。")
    prompt = f"问题：{question}\n\n证据：\n{context[:6000]}"
    data = await _llm_json(prompt, system)
    return {"sufficient": bool(data.get("sufficient")), "refine": (data.get("refine") or "").strip()}


# 单文档模式放大召回，补偿「召回后按文档过滤」的损耗（见设计 §4.4）
_SINGLE_DOC_TOP_K = 60
_SINGLE_DOC_CHUNK_TOP_K = 40


async def _retrieve(rag, query: str, doc_id=None) -> dict:
    """对一个子问题取原始检索结果（实体/关系/chunk + 引用）。单文档模式放大召回。"""
    if doc_id:
        param = QueryParam(mode="mix", top_k=_SINGLE_DOC_TOP_K, chunk_top_k=_SINGLE_DOC_CHUNK_TOP_K)
    else:
        param = QueryParam(mode="mix")
    result = await rag.aquery_data(query, param=param)
    return result.get("data", {}) if isinstance(result, dict) else {}


def _aggregate(datas: list[dict], doc_id=None) -> tuple[list[dict], dict]:
    """跨子问题/多轮聚合 chunk 并去重；汇总 reference_id→file_path。

    单文档模式（doc_id 非空）：只保留 chunk_id 属于该文档的 chunk（chunk_id 形如
    `{doc_id}-chunk-N`），引用也只保留被这些 chunk 引用到的，避免串档。
    """
    chunks: dict[str, dict] = {}
    all_refs: dict[str, str] = {}
    for data in datas:
        for c in data.get("chunks", []) or []:
            cid = c.get("chunk_id") or ""
            if doc_id and not cid.startswith(doc_id):
                continue
            key = cid or (c.get("content", "")[:80])
            if key:
                chunks[key] = c
        for r in data.get("references", []) or []:
            if r.get("reference_id"):
                all_refs[r["reference_id"]] = r.get("file_path", "")
    kept = list(chunks.values())
    if not doc_id:
        return kept, all_refs
    used = {c.get("reference_id") for c in kept if c.get("reference_id")}
    refs = {rid: fp for rid, fp in all_refs.items() if rid in used}
    return kept, refs


def _build_context(chunks: list[dict], refs: dict) -> tuple[str, list[dict]]:
    """构造带编号引用的证据文本与 citations 列表。"""
    ordered = list(refs.items())  # [(reference_id, file_path)]
    ref_index = {rid: i + 1 for i, (rid, _) in enumerate(ordered)}
    lines = []
    for c in chunks:
        rid = c.get("reference_id")
        tag = f"[{ref_index.get(rid, '?')}]"
        lines.append(f"{tag} {c.get('content', '').strip()}")
    citations = [{"index": i + 1, "reference_id": rid, "file_path": fp}
                 for i, (rid, fp) in enumerate(ordered)]
    return "\n\n".join(lines), citations


async def _stream_answer(question, context, history):
    """基于聚合证据流式生成答案（带引用标注要求）。"""
    system = ("你是知识库问答助手。仅依据给定证据回答，并在引用处标注来源编号如 [1]。"
              "证据不足时如实说明，不要编造。")
    hist = [{"role": m["role"], "content": m["content"]}
            for m in (history or [])[-6:]
            if m.get("role") in ("user", "assistant") and m.get("content")]
    prompt = f"证据：\n{context[:8000]}\n\n问题：{question}"
    stream = await openai_complete_if_cache(
        settings.deepseek_chat_model, prompt, system_prompt=system, history_messages=hist,
        base_url=settings.deepseek_base_url, api_key=settings.deepseek_api_key, stream=True)
    async for token in stream:
        if token:
            yield token


async def run_agent(kb_id, question: str, doc_id=None, history=None):
    """驱动 agent loop，产出 SSE 事件流。"""
    try:
        async with asyncio.timeout(settings.agent_timeout_s):
            rag = await lightrag_service.get_lightrag(kb_id)
            mode = "single_doc" if doc_id else "kb"
            yield _sse({"type": "status", "stage": "route", "mode": mode})
            yield _sse({"type": "status", "stage": "decompose"})
            subqueries = await _decompose(question)
            yield _sse({"type": "status", "stage": "decomposed", "subqueries": subqueries})

            collected: list[dict] = []
            queries = list(subqueries)
            for it in range(1, settings.agent_max_iters + 1):
                yield _sse({"type": "status", "stage": "retrieve", "iter": it})
                for q in queries:
                    collected.append(await _retrieve(rag, q, doc_id))
                chunks, refs = _aggregate(collected, doc_id)
                context, citations = _build_context(chunks, refs)
                grade = await _grade(question, context)
                yield _sse({"type": "status", "stage": "grade", "iter": it,
                            "sufficient": grade["sufficient"]})
                if grade["sufficient"] or it >= settings.agent_max_iters or not grade["refine"]:
                    break
                queries = [grade["refine"]]  # 下一轮用改写后的检索词

            yield _sse({"type": "status", "stage": "generate"})
            async for token in _stream_answer(question, context, history):
                yield _sse({"type": "token", "text": token})
            yield _sse({"type": "citations", "items": citations})
            yield _sse({"type": "done"})
    except asyncio.TimeoutError:
        yield _sse({"type": "error", "stage": "timeout",
                    "message": f"agent timeout after {settings.agent_timeout_s}s"})
    except Exception as exc:  # noqa: BLE001 — 兜底，避免 SSE 连接挂死
        yield _sse({"type": "error", "message": str(exc)[:500]})
