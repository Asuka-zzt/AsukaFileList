"""API 路由汇总"""
from fastapi import APIRouter
from app.api import kb_router

api_router = APIRouter()
api_router.include_router(kb_router.router, prefix="/kb", tags=["知识库"])
