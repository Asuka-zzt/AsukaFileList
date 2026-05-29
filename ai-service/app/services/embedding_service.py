"""DeepSeek Embedding API 封装"""
import httpx
from tenacity import retry, stop_after_attempt, wait_exponential
from app.core.config import settings


@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
async def get_embedding(text: str) -> list[float]:
    """
    调用 DeepSeek Embedding API，返回 1024 维向量。
    自动重试 3 次（指数退避）。
    """
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            f"{settings.deepseek_base_url}/embeddings",
            headers={"Authorization": f"Bearer {settings.deepseek_api_key}"},
            json={"model": settings.deepseek_embed_model, "input": text},
        )
        resp.raise_for_status()
        data = resp.json()
        return data["data"][0]["embedding"]


async def get_embeddings_batch(texts: list[str]) -> list[list[float]]:
    """批量获取 embedding（串行调用，避免超限）"""
    results = []
    for text in texts:
        emb = await get_embedding(text)
        results.append(emb)
    return results
