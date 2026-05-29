"""文档索引服务：下载文件 → 解析文本 → 切分 → embedding → 存入 pgvector"""
import httpx
import io
from typing import Optional

import pypdf
import docx

from app.core.config import settings
from app.core.database import AsyncSessionLocal
from app.models.vector_doc import VectorDoc
from app.services.embedding_service import get_embeddings_batch

CHUNK_SIZE = 512      # 每个文本 chunk 的字符数
CHUNK_OVERLAP = 64    # 相邻 chunk 重叠字符数


async def index_file(user_file_id: int, user_id: int,
                     file_download_url: str, mime_type: str) -> int:
    """
    完整索引流程：
    1. 从 Java 服务下载文件内容
    2. 按 mime_type 解析文本
    3. 按 CHUNK_SIZE 切分
    4. 批量调用 embedding API
    5. 写入 pgvector 表
    返回写入的 chunk 数量。
    """
    raw_bytes = await _download_file(file_download_url)
    text      = _extract_text(raw_bytes, mime_type)
    chunks    = _split_text(text, CHUNK_SIZE, CHUNK_OVERLAP)

    if not chunks:
        return 0

    embeddings = await get_embeddings_batch(chunks)

    async with AsyncSessionLocal() as session:
        # 删除旧索引（重新索引场景）
        from sqlalchemy import delete
        await session.execute(
            delete(VectorDoc).where(VectorDoc.user_file_id == user_file_id)
        )
        docs = [
            VectorDoc(
                user_id=user_id,
                user_file_id=user_file_id,
                chunk_index=i,
                content=chunk,
                embedding=emb,
            )
            for i, (chunk, emb) in enumerate(zip(chunks, embeddings))
        ]
        session.add_all(docs)
        await session.commit()

    return len(docs)


async def _download_file(url: str) -> bytes:
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.get(
            url,
            headers={"Authorization": f"Bearer {settings.master_token}"}
        )
        resp.raise_for_status()
        return resp.content


def _extract_text(data: bytes, mime_type: str) -> str:
    """根据 MIME 类型提取纯文本"""
    if "pdf" in mime_type:
        reader = pypdf.PdfReader(io.BytesIO(data))
        return "\n".join(page.extract_text() or "" for page in reader.pages)
    if "word" in mime_type or "docx" in mime_type:
        doc = docx.Document(io.BytesIO(data))
        return "\n".join(p.text for p in doc.paragraphs)
    # 纯文本 / markdown / csv 等
    return data.decode("utf-8", errors="replace")


def _split_text(text: str, chunk_size: int, overlap: int) -> list[str]:
    """滑动窗口切分文本"""
    if not text.strip():
        return []
    chunks, start = [], 0
    while start < len(text):
        end = min(start + chunk_size, len(text))
        chunks.append(text[start:end])
        start += chunk_size - overlap
    return [c.strip() for c in chunks if c.strip()]
