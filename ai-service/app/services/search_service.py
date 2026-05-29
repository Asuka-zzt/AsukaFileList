"""语义搜索 & 混合搜索服务"""
from sqlalchemy import select, text
from app.core.database import AsyncSessionLocal
from app.models.vector_doc import VectorDoc
from app.services.embedding_service import get_embedding


async def semantic_search(user_id: int, query: str, limit: int = 10) -> list[dict]:
    """
    纯向量相似度搜索（cosine distance）
    使用 pgvector 的 <=> 运算符
    """
    query_emb = await get_embedding(query)

    async with AsyncSessionLocal() as session:
        stmt = (
            select(
                VectorDoc.user_file_id,
                VectorDoc.chunk_index,
                VectorDoc.content,
                (1 - VectorDoc.embedding.cosine_distance(query_emb)).label("score"),
            )
            .where(VectorDoc.user_id == user_id)
            .order_by(VectorDoc.embedding.cosine_distance(query_emb))
            .limit(limit)
        )
        rows = (await session.execute(stmt)).all()

    return [
        {
            "userFileId":  r.user_file_id,
            "chunkIndex":  r.chunk_index,
            "content":     r.content,
            "score":       float(r.score),
        }
        for r in rows
    ]


async def hybrid_search(user_id: int, query: str, limit: int = 10) -> list[dict]:
    """
    混合搜索：向量搜索 + PostgreSQL 全文检索，RRF 融合排名
    """
    vector_results = await semantic_search(user_id, query, limit * 2)

    async with AsyncSessionLocal() as session:
        stmt = text("""
            SELECT user_file_id, chunk_index, content,
                   ts_rank(to_tsvector('simple', content),
                           plainto_tsquery('simple', :query)) AS fts_score
            FROM vector_doc
            WHERE user_id = :user_id
              AND to_tsvector('simple', content) @@ plainto_tsquery('simple', :query)
            ORDER BY fts_score DESC
            LIMIT :limit
        """)
        rows = (await session.execute(stmt, {"user_id": user_id, "query": query, "limit": limit * 2})).all()

    fts_results = [
        {"userFileId": r.user_file_id, "chunkIndex": r.chunk_index,
         "content": r.content, "score": float(r.fts_score)}
        for r in rows
    ]

    # Reciprocal Rank Fusion
    return _rrf_merge(vector_results, fts_results, limit)


def _rrf_merge(list_a: list[dict], list_b: list[dict], limit: int, k: int = 60) -> list[dict]:
    """RRF 融合：score = Σ 1/(k + rank)"""
    scores: dict[tuple, float] = {}
    docs:   dict[tuple, dict]  = {}

    for rank, item in enumerate(list_a):
        key = (item["userFileId"], item["chunkIndex"])
        scores[key] = scores.get(key, 0.0) + 1.0 / (k + rank + 1)
        docs[key]   = item

    for rank, item in enumerate(list_b):
        key = (item["userFileId"], item["chunkIndex"])
        scores[key] = scores.get(key, 0.0) + 1.0 / (k + rank + 1)
        docs[key]   = item

    sorted_keys = sorted(scores, key=scores.__getitem__, reverse=True)[:limit]
    return [{**docs[k], "score": scores[k]} for k in sorted_keys]
