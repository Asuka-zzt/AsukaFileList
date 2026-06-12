"""Tests for agent loop iteration, streaming, and failure behavior."""
import asyncio
import json

import pytest

from app.services import agent_service


def _events(parts: list[str]) -> list[dict]:
    return [json.loads(part.removeprefix("data: ").strip()) for part in parts]


@pytest.mark.asyncio
async def test_agent_stops_when_evidence_is_sufficient(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Sufficient evidence produces tokens, citations, and done in one round."""
    class FakeRag:
        pass

    async def fake_get(kb_id):
        assert kb_id == 1
        return FakeRag()

    async def fake_decompose(question):
        return ["subquestion"]

    async def fake_retrieve(rag, query, doc_id=None):
        return {
            "chunks": [
                {
                    "chunk_id": "doc-1-chunk-0",
                    "content": "evidence",
                    "reference_id": "ref-1",
                }
            ],
            "references": [{"reference_id": "ref-1", "file_path": "note.md"}],
        }

    async def fake_grade(question, context):
        return {"sufficient": True, "refine": ""}

    async def fake_stream(question, context, history):
        yield "answer"

    monkeypatch.setattr(agent_service.lightrag_service, "get_lightrag", fake_get)
    monkeypatch.setattr(agent_service, "_decompose", fake_decompose)
    monkeypatch.setattr(agent_service, "_retrieve", fake_retrieve)
    monkeypatch.setattr(agent_service, "_grade", fake_grade)
    monkeypatch.setattr(agent_service, "_stream_answer", fake_stream)

    parts = [part async for part in agent_service.run_agent(1, "question")]
    events = _events(parts)

    assert [event["type"] for event in events][-3:] == [
        "token",
        "citations",
        "done",
    ]
    grades = [event for event in events if event.get("stage") == "grade"]
    assert grades == [
        {"type": "status", "stage": "grade", "iter": 1, "sufficient": True}
    ]


@pytest.mark.asyncio
async def test_agent_respects_iteration_limit(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Insufficient evidence cannot exceed the configured iteration limit."""
    retrievals: list[str] = []

    async def fake_get(kb_id):
        return object()

    async def fake_decompose(question):
        return ["initial"]

    async def fake_retrieve(rag, query, doc_id=None):
        retrievals.append(query)
        return {"chunks": [], "references": []}

    async def fake_grade(question, context):
        return {"sufficient": False, "refine": "refined"}

    async def fake_stream(question, context, history):
        if False:
            yield ""

    monkeypatch.setattr(agent_service.settings, "agent_max_iters", 2)
    monkeypatch.setattr(agent_service.lightrag_service, "get_lightrag", fake_get)
    monkeypatch.setattr(agent_service, "_decompose", fake_decompose)
    monkeypatch.setattr(agent_service, "_retrieve", fake_retrieve)
    monkeypatch.setattr(agent_service, "_grade", fake_grade)
    monkeypatch.setattr(agent_service, "_stream_answer", fake_stream)

    events = _events(
        [part async for part in agent_service.run_agent(2, "question")]
    )

    assert retrievals == ["initial", "refined"]
    assert events[-1] == {"type": "done"}


@pytest.mark.asyncio
async def test_agent_timeout_returns_error_event(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """The overall timeout becomes a terminal SSE error event."""
    async def slow_get(kb_id):
        await asyncio.sleep(0.05)

    monkeypatch.setattr(agent_service.settings, "agent_timeout_s", 0.001)
    monkeypatch.setattr(agent_service.lightrag_service, "get_lightrag", slow_get)

    events = _events(
        [part async for part in agent_service.run_agent(3, "question")]
    )

    assert events == [
        {
            "type": "error",
            "stage": "timeout",
            "message": "agent timeout after 0.001s",
        }
    ]
