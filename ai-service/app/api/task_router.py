"""Celery 任务状态查询"""
from fastapi import APIRouter, Depends
from celery.result import AsyncResult
from app.core.security import verify_api_key
from app.core.celery_app import celery_app

router = APIRouter(dependencies=[Depends(verify_api_key)])


@router.get("/{task_id}")
async def get_task_status(task_id: str):
    result = AsyncResult(task_id, app=celery_app)
    return {
        "taskId": task_id,
        "state":  result.state,
        "result": result.result if result.ready() else None,
    }
