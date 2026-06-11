"""Tests for internal knowledge-base HTTP routes."""
import json

from fastapi.testclient import TestClient

from app.api import kb_router
from app.main import app


client = TestClient(app)
HEADERS = {"X-API-Key": "test-api-key"}


def test_routes_require_api_key() -> None:
    """Internal routes reject missing and invalid API keys."""
    assert client.get("/kb/task/unknown").status_code == 422
    assert (
        client.get("/kb/task/unknown", headers={"X-API-Key": "wrong"}).status_code
        == 403
    )


def test_submit_index_dispatches_celery_task(monkeypatch) -> None:
    """The index endpoint forwards all document metadata to Celery."""
    captured: dict = {}

    class FakeTask:
        id = "task-123"

    def fake_delay(*args):
        captured["args"] = args
        return FakeTask()

    monkeypatch.setattr(kb_router.task_kb_index, "delay", fake_delay)
    response = client.post(
        "/kb/12/index",
        headers=HEADERS,
        json={
            "docId": "doc-12",
            "fileDownloadUrl": "http://java/file",
            "fileName": "note.md",
            "mimeType": "text/markdown",
            "docType": "note",
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "taskId": "task-123",
        "status": "pending",
        "error": None,
    }
    assert captured["args"] == (
        12,
        "doc-12",
        "http://java/file",
        "text/markdown",
        "note.md",
        "note",
    )


def test_task_state_mapping(monkeypatch) -> None:
    """Celery states are mapped to the public document status vocabulary."""
    class FakeResult:
        state = "FAILURE"
        result = RuntimeError("index failed")

    monkeypatch.setattr(kb_router, "AsyncResult", lambda *args, **kwargs: FakeResult())
    response = client.get("/kb/task/task-1", headers=HEADERS)

    assert response.status_code == 200
    assert response.json() == {
        "taskId": "task-1",
        "status": "failed",
        "error": "index failed",
    }


def test_delete_routes_delegate(monkeypatch) -> None:
    """Workspace and document deletion delegate to LightRAG."""
    calls: list[tuple] = []

    async def delete_kb(kb_id):
        calls.append(("kb", kb_id))

    async def delete_doc(kb_id, doc_id):
        calls.append(("doc", kb_id, doc_id))

    monkeypatch.setattr(kb_router.lightrag_service, "delete_workspace", delete_kb)
    monkeypatch.setattr(
        kb_router.lightrag_service, "adelete_by_doc_id", delete_doc
    )

    assert client.delete("/kb/4", headers=HEADERS).status_code == 200
    assert client.delete("/kb/4/doc/doc-4", headers=HEADERS).status_code == 200
    assert calls == [("kb", 4), ("doc", 4, "doc-4")]


def test_chat_streams_agent_events(monkeypatch) -> None:
    """Chat responses preserve the agent SSE event stream."""
    async def fake_agent(kb_id, question, doc_id, history):
        assert (kb_id, question, doc_id) == (8, "question", "doc-8")
        assert history == [{"role": "user", "content": "previous"}]
        yield 'data: {"type":"token","text":"answer"}\n\n'
        yield 'data: {"type":"done"}\n\n'

    monkeypatch.setattr(kb_router.agent_service, "run_agent", fake_agent)
    response = client.post(
        "/kb/8/chat",
        headers=HEADERS,
        json={
            "question": "question",
            "docId": "doc-8",
            "history": [{"role": "user", "content": "previous"}],
        },
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    payloads = [
        json.loads(part.removeprefix("data: ").strip())
        for part in response.text.strip().split("\n\n")
    ]
    assert payloads == [
        {"type": "token", "text": "answer"},
        {"type": "done"},
    ]
