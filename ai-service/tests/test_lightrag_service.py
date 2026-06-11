"""Tests for LightRAG workspace and instance orchestration."""
import os

import pytest

from app.services import lightrag_service


def test_workspace_name_uses_configured_prefix(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Knowledge base ids map to stable workspace names."""
    monkeypatch.setattr(
        lightrag_service.settings, "lightrag_workspace_prefix", "tenant_kb_"
    )
    assert lightrag_service.workspace_of(42) == "tenant_kb_42"


def test_ensure_pg_env_parses_dsn(monkeypatch: pytest.MonkeyPatch) -> None:
    """The native asyncpg DSN is converted to LightRAG environment fields."""
    for key in (
        "_ASUKA_PG_ENV_SET",
        "POSTGRES_HOST",
        "POSTGRES_PORT",
        "POSTGRES_USER",
        "POSTGRES_PASSWORD",
        "POSTGRES_DATABASE",
    ):
        monkeypatch.delenv(key, raising=False)
    monkeypatch.setattr(
        lightrag_service.settings,
        "postgres_age_dsn",
        "postgresql://user%40name:p%40ss@db.example:5544/graph_db",
    )

    lightrag_service._ensure_pg_env()

    assert os.environ["POSTGRES_HOST"] == "db.example"
    assert os.environ["POSTGRES_PORT"] == "5544"
    assert os.environ["POSTGRES_USER"] == "user@name"
    assert os.environ["POSTGRES_PASSWORD"] == "p@ss"
    assert os.environ["POSTGRES_DATABASE"] == "graph_db"


@pytest.mark.asyncio
async def test_get_lightrag_caches_instance(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A workspace reuses its initialized LightRAG instance."""
    created: list[str] = []
    sentinel = object()

    async def fake_create(workspace: str):
        created.append(workspace)
        return sentinel

    lightrag_service._rag_instances.clear()
    monkeypatch.setattr(lightrag_service, "_create_lightrag", fake_create)

    first = await lightrag_service.get_lightrag(7)
    second = await lightrag_service.get_lightrag(7)

    assert first is sentinel
    assert second is sentinel
    assert created == ["kb_7"]
    lightrag_service._rag_instances.clear()


@pytest.mark.asyncio
async def test_insert_and_delete_delegate_to_workspace(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Index and delete operations delegate with document metadata intact."""
    calls: list[tuple] = []

    class FakeRag:
        async def ainsert(self, text, ids=None, file_paths=None):
            calls.append(("insert", text, ids, file_paths))

        async def adelete_by_doc_id(self, doc_id):
            calls.append(("delete", doc_id))

    async def fake_get(kb_id):
        assert kb_id == 9
        return FakeRag()

    monkeypatch.setattr(lightrag_service, "get_lightrag", fake_get)
    await lightrag_service.ainsert(
        9, "content", ids=["doc-1"], file_paths=["note.md"]
    )
    await lightrag_service.adelete_by_doc_id(9, "doc-1")

    assert calls == [
        ("insert", "content", ["doc-1"], ["note.md"]),
        ("delete", "doc-1"),
    ]
