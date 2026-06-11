"""FastAPI 应用入口"""
import structlog
from fastapi import FastAPI
from app.api.router import api_router

logger = structlog.get_logger()

app = FastAPI(
    title="AsukaCloudDisk AI Service",
    version="2.0.0",
    description="Graph RAG 知识库（LightRAG + DeepSeek + bge-m3）",
)

app.include_router(api_router)


@app.get("/health", tags=["健康检查"])
async def health():
    return {"status": "ok"}
