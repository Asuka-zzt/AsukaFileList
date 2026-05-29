"""搜索接口"""
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from app.core.security import verify_api_key
from app.services.search_service import semantic_search, hybrid_search

router = APIRouter(dependencies=[Depends(verify_api_key)])


class SearchRequest(BaseModel):
    userId: int
    query:  str
    limit:  int = 10


@router.post("/semantic")
async def api_semantic_search(req: SearchRequest):
    return await semantic_search(req.userId, req.query, req.limit)


@router.post("/hybrid")
async def api_hybrid_search(req: SearchRequest):
    return await hybrid_search(req.userId, req.query, req.limit)
