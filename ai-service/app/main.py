"""FastAPI 应用入口"""
import structlog
from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.api.router import api_router
from app.core.database import init_db

logger = structlog.get_logger()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时初始化数据库表结构"""
    logger.info("ai-service starting, initializing database...")
    await init_db()
    logger.info("database initialized")
    yield
    logger.info("ai-service shutting down")


app = FastAPI(
    title="AsukaCloudDisk AI Service",
    version="1.0.0",
    description="语义搜索 & RAG 问答 & 知识图谱",
    lifespan=lifespan,
)

app.include_router(api_router)


@app.get("/health", tags=["健康检查"])
async def health():
    return {"status": "ok"}
