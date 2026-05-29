"""文档索引接口（供 Java 服务异步调用）"""
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from app.core.security import verify_api_key
from app.tasks.index_tasks import task_index_file

router = APIRouter(dependencies=[Depends(verify_api_key)])


class IndexRequest(BaseModel):
    userFileId:      int
    userId:          int
    fileDownloadUrl: str
    mimeType:        str


@router.post("/index")
async def submit_index(req: IndexRequest):
    """提交索引任务到 Celery，立即返回 taskId"""
    task = task_index_file.delay(
        req.userFileId, req.userId, req.fileDownloadUrl, req.mimeType
    )
    return {"taskId": task.id}
