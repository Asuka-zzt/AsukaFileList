"""Tests for the knowledge-base indexing task state machine."""
import pytest

from app.tasks import kb_index_tasks


@pytest.mark.asyncio
async def test_index_run_reports_state_sequence(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A successful index reports parsing, indexing, and indexed in order."""
    statuses: list[tuple] = []
    inserts: list[tuple] = []
    calls: list[str] = []

    async def fake_report(doc_id, status, lightrag_doc_id=None, error=None):
        statuses.append((doc_id, status, lightrag_doc_id, error))

    async def fake_parse(url, mime_type, file_name):
        return "# parsed"

    async def fake_delete(kb_id, doc_id):
        calls.append("delete")

    async def fake_insert(kb_id, text, ids=None, file_paths=None):
        calls.append("insert")
        inserts.append((kb_id, text, ids, file_paths))

    monkeypatch.setattr(kb_index_tasks, "report_status", fake_report)
    monkeypatch.setattr(kb_index_tasks.parse_service, "parse_document", fake_parse)
    monkeypatch.setattr(kb_index_tasks.lightrag_service, "adelete_by_doc_id", fake_delete)
    monkeypatch.setattr(kb_index_tasks.lightrag_service, "ainsert", fake_insert)

    result = await kb_index_tasks._run(
        5, "doc-5", "http://java/file", "text/markdown", "note.md"
    )

    assert result == {"status": "indexed", "docId": "doc-5"}
    assert [status[1] for status in statuses] == [
        "parsing",
        "indexing",
        "indexed",
    ]
    assert inserts == [(5, "# parsed", ["doc-5"], ["note.md"])]
    # 幂等：删除旧 doc_id 必须在插入之前
    assert calls == ["delete", "insert"]


@pytest.mark.asyncio
async def test_index_run_reports_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A parsing failure is reported and re-raised for Celery retry."""
    statuses: list[tuple] = []
    deletes: list[tuple] = []

    async def fake_report(doc_id, status, lightrag_doc_id=None, error=None):
        statuses.append((status, error))

    async def fail_parse(url, mime_type, file_name):
        raise ValueError("bad document")

    async def fake_delete(kb_id, doc_id):
        deletes.append((kb_id, doc_id))

    monkeypatch.setattr(kb_index_tasks, "report_status", fake_report)
    monkeypatch.setattr(kb_index_tasks.parse_service, "parse_document", fail_parse)
    monkeypatch.setattr(kb_index_tasks.lightrag_service, "adelete_by_doc_id", fake_delete)

    with pytest.raises(ValueError, match="bad document"):
        await kb_index_tasks._run(
            5, "doc-5", "http://java/file", "application/pdf", "paper.pdf"
        )

    assert statuses == [("parsing", None), ("failed", "bad document")]
    # 解析失败不得删除旧索引（保住已有内容）
    assert deletes == []
