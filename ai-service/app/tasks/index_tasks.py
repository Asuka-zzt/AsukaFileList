"""Celery 异步任务：文件索引"""
import asyncio
from app.core.celery_app import celery_app
from app.services.index_service import index_file


@celery_app.task(bind=True, name="tasks.index_file", max_retries=3, default_retry_delay=60)
def task_index_file(self, user_file_id: int, user_id: int,
                    file_download_url: str, mime_type: str) -> dict:
    """
    Celery 任务：对单个文件建立语义索引
    bind=True 允许访问 self（用于重试）
    """
    try:
        count = asyncio.get_event_loop().run_until_complete(
            index_file(user_file_id, user_id, file_download_url, mime_type)
        )
        return {"status": "SUCCESS", "chunks": count}
    except Exception as exc:
        raise self.retry(exc=exc)
