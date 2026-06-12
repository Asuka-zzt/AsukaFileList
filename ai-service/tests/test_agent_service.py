"""Tests for deterministic agent helper behavior."""
import pytest

from app.services import agent_service


@pytest.mark.asyncio
async def test_decompose_falls_back_to_original_question(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Invalid LLM decomposition keeps the original question."""
    async def fake_json(prompt: str, system: str) -> dict:
        return {"subqueries": ["", None, 123]}

    monkeypatch.setattr(agent_service, "_llm_json", fake_json)
    assert await agent_service._decompose("original") == ["original"]


def test_aggregate_filters_single_document_and_deduplicates() -> None:
    """Single-document mode drops chunks and references from other documents."""
    data = {
        "chunks": [
            {
                "chunk_id": "doc-1-chunk-0",
                "content": "kept",
                "reference_id": "ref-1",
            },
            {
                "chunk_id": "doc-2-chunk-0",
                "content": "removed",
                "reference_id": "ref-2",
            },
            {
                "chunk_id": "doc-1-chunk-0",
                "content": "latest",
                "reference_id": "ref-1",
            },
        ],
        "references": [
            {"reference_id": "ref-1", "file_path": "one.md"},
            {"reference_id": "ref-2", "file_path": "two.md"},
        ],
    }

    chunks, refs = agent_service._aggregate([data], "doc-1")

    assert [chunk["content"] for chunk in chunks] == ["latest"]
    assert refs == {"ref-1": "one.md"}


def test_build_context_numbers_citations() -> None:
    """Context and citation metadata use the same stable numbering."""
    chunks = [
        {"content": "alpha", "reference_id": "r1"},
        {"content": "beta", "reference_id": "r2"},
    ]
    context, citations = agent_service._build_context(
        chunks, {"r1": "one.md", "r2": "two.md"}
    )

    assert context == "[1] alpha\n\n[2] beta"
    assert citations == [
        {"index": 1, "reference_id": "r1", "file_path": "one.md"},
        {"index": 2, "reference_id": "r2", "file_path": "two.md"},
    ]
