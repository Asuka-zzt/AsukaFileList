"""索引状态回写 Java 主服务。

AI 服务无 MySQL 访问，文档状态机（pending→parsing→indexing→indexed/failed）通过
回调 Java 内部接口落库。回调为尽力而为：失败只告警，不阻断索引任务本身
（Celery 任务结果仍可经 GET /kb/task/{id} 兜底查询）。
"""
import httpx
import structlog

from app.core.config import settings

logger = structlog.get_logger()


async def report_status(doc_id: str, status: str,
                        lightrag_doc_id: str | None = None,
                        error: str | None = None) -> None:
    """回写某文档的索引状态到 Java（POST /internal/kb/index-callback）。"""
    payload = {
        "docId": doc_id,
        "status": status,
        "lightragDocId": lightrag_doc_id,
        "error": error,
    }
    url = settings.master_url.rstrip("/") + "/internal/kb/index-callback"
    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(
                url, json=payload,
                headers={"Authorization": f"Bearer {settings.master_token}"})
            resp.raise_for_status()
    except Exception as exc:  # noqa: BLE001 — 回调失败不应中断索引
        logger.warning("kb status callback failed",
                       doc_id=doc_id, status=status, error=str(exc))
