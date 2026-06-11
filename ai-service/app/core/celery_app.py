"""Celery 应用实例"""
from celery import Celery
from app.core.config import settings

celery_app = Celery(
    "ai_service",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.tasks.index_tasks", "app.tasks.kb_index_tasks"],
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="Asia/Shanghai",
    enable_utc=True,
    task_track_started=True,
    task_acks_late=True,          # 任务完成后才 ack，防止崩溃丢任务
    worker_prefetch_multiplier=1, # 每个 worker 一次只取一个任务
)
