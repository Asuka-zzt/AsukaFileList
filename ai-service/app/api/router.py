"""API 路由汇总"""
from fastapi import APIRouter
from app.api import index_router, search_router, chat_router, task_router, kb_router

api_router = APIRouter()
api_router.include_router(index_router.router, prefix="/internal", tags=["索引（内部）"])
api_router.include_router(search_router.router, prefix="/v1/search", tags=["搜索"])
api_router.include_router(chat_router.router,   prefix="/v1",        tags=["问答"])
api_router.include_router(task_router.router,   prefix="/v1/tasks",  tags=["任务状态"])
api_router.include_router(kb_router.router,     prefix="/kb",        tags=["知识库"])
