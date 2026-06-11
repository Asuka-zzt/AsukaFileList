"""知识库 API（内部，X-API-Key 鉴权）：索引 / 删除 / 任务状态。

问答接口（/kb/{id}/chat）在 P5 实现，此处先落索引链路。
"""
from celery.result import AsyncResult
from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.core.celery_app import celery_app
from app.core.security import verify_api_key
from app.services import lightrag_service
from app.tasks.kb_index_tasks import task_kb_index

router = APIRouter(dependencies=[Depends(verify_api_key)])


class KbIndexRequest(BaseModel):
    """与 Java AiKbIndexRequest 对齐（camelCase 字段）。"""
    docId: str
    fileDownloadUrl: str
    fileName: str
    mimeType: str | None = None
    docType: str | None = "paper"


def _task_response(task_id, status, error=None):
    return {"taskId": task_id, "status": status, "error": error}


@router.post("/{kb_id}/index")
async def submit_index(kb_id: int, req: KbIndexRequest):
    """投递解析+增量索引任务，返回 taskId。"""
    task = task_kb_index.delay(
        kb_id, req.docId, req.fileDownloadUrl, req.mimeType, req.fileName, req.docType)
    return _task_response(task.id, "pending")


@router.delete("/{kb_id}")
async def delete_kb(kb_id: int):
    """删除整个 KB 的 LightRAG workspace。"""
    await lightrag_service.delete_workspace(kb_id)
    return _task_response(None, "deleted")


@router.delete("/{kb_id}/doc/{doc_id}")
async def delete_doc(kb_id: int, doc_id: str):
    """按 doc_id 删除某文档的索引。"""
    await lightrag_service.adelete_by_doc_id(kb_id, doc_id)
    return _task_response(None, "deleted")


# Celery state → 文档状态机
_STATE_MAP = {
    "PENDING": "pending",
    "RECEIVED": "pending",
    "STARTED": "indexing",
    "RETRY": "indexing",
    "SUCCESS": "indexed",
    "FAILURE": "failed",
}


@router.get("/task/{task_id}")
async def get_task(task_id: str):
    """查询索引任务状态（前端轮询兜底；权威状态以回调落库为准）。"""
    result = AsyncResult(task_id, app=celery_app)
    status = _STATE_MAP.get(result.state, result.state.lower())
    error = str(result.result) if result.state == "FAILURE" else None
    return _task_response(task_id, status, error)
