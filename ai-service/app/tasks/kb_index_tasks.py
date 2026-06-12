"""Celery 任务：知识库文档增量索引（下载 → 解析 → ainsert）。

同一 KB 串行（Redis 分布式锁 `lightrag:lock:kb_{id}`，LightRAG 对同 workspace 并发
ainsert 不安全），不同 KB 可并行。状态经 kb_callback 回写 Java。
"""
import asyncio

import redis

from app.core.celery_app import celery_app
from app.core.config import settings
from app.services import lightrag_service, parse_service
from app.services.kb_callback import report_status

# 每个 worker 进程持有一个常驻事件循环：跨任务复用 LightRAG 实例与 asyncpg 连接池
# （asyncpg 连接池绑定创建它的事件循环，故不能每次任务新建/关闭循环）。
_loop = asyncio.new_event_loop()

_redis = redis.Redis.from_url(settings.resolved_redis_url())

_LOCK_TIMEOUT = 1800     # 锁最长持有 30min（防 worker 崩溃后死锁）
_LOCK_BLOCKING = 900     # 最长等锁 15min


@celery_app.task(bind=True, name="tasks.kb_index", max_retries=2, default_retry_delay=30)
def task_kb_index(self, kb_id, doc_id, file_download_url,
                  mime_type, file_name, doc_type=None):
    """对同一 KB 串行执行索引；等锁超时则重试。"""
    lock = _redis.lock(f"lightrag:lock:kb_{kb_id}",
                       timeout=_LOCK_TIMEOUT, blocking_timeout=_LOCK_BLOCKING)
    if not lock.acquire():
        raise self.retry()
    try:
        return _loop.run_until_complete(
            _run(kb_id, doc_id, file_download_url, mime_type, file_name))
    finally:
        try:
            lock.release()
        except redis.exceptions.LockError:
            pass


async def _run(kb_id, doc_id, url, mime_type, file_name) -> dict:
    """下载→解析→增量索引，逐阶段回写状态；失败标 failed 并抛出（触发 Celery 重试）。"""
    try:
        await report_status(doc_id, "parsing")
        text = await parse_service.parse_document(url, mime_type, file_name)
        await report_status(doc_id, "indexing")
        await lightrag_service.ainsert(kb_id, text, ids=[doc_id], file_paths=[file_name])
        await report_status(doc_id, "indexed", lightrag_doc_id=doc_id)
        return {"status": "indexed", "docId": doc_id}
    except Exception as exc:
        await report_status(doc_id, "failed", error=str(exc)[:1000])
        raise
