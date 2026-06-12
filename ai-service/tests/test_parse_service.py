"""Tests for document download and parsing."""
from pathlib import Path

import httpx
import pytest

from app.services import parse_service


def test_parse_markdown_bytes() -> None:
    """Markdown is decoded while an empty document is rejected."""
    assert parse_service.parse_markdown_bytes(b"# Title\ncontent") == "# Title\ncontent"
    with pytest.raises(parse_service.ParseError, match="empty markdown"):
        parse_service.parse_markdown_bytes(b" \n")


@pytest.mark.asyncio
async def test_parse_document_routes_by_type(monkeypatch: pytest.MonkeyPatch) -> None:
    """Markdown is handled directly and unsupported types fail explicitly."""
    async def fake_download(url: str) -> bytes:
        assert url == "http://java/internal"
        return b"# Note"

    monkeypatch.setattr(parse_service, "download_file", fake_download)
    text = await parse_service.parse_document(
        "http://java/internal", "text/markdown", "note.md"
    )
    assert text == "# Note"

    with pytest.raises(parse_service.ParseError, match="unsupported file type"):
        await parse_service.parse_document(
            "http://java/internal", "text/plain", "note.txt"
        )


def test_parse_pdf_uses_converter(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """The PDF converter output is collected as Markdown."""
    def fake_convert(**kwargs) -> None:
        output = Path(kwargs["output_dir"]) / "result.md"
        output.write_text("# Parsed PDF", encoding="utf-8")

    monkeypatch.setattr(parse_service.opendataloader_pdf, "convert", fake_convert)
    assert parse_service.parse_pdf_bytes(b"%PDF", "paper.pdf") == "# Parsed PDF"


def test_parse_pdf_wraps_converter_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Converter errors become stable ParseError instances."""
    def fail_convert(**kwargs) -> None:
        raise RuntimeError("converter unavailable")

    monkeypatch.setattr(parse_service.opendataloader_pdf, "convert", fail_convert)
    with pytest.raises(parse_service.ParseError, match="converter unavailable"):
        parse_service.parse_pdf_bytes(b"%PDF", "paper.pdf")


@pytest.mark.asyncio
async def test_download_file_sends_master_token(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Internal downloads carry the configured bearer token."""
    captured: dict = {}

    class FakeResponse:
        content = b"payload"

        def raise_for_status(self) -> None:
            return None

    class FakeClient:
        def __init__(self, timeout: int):
            captured["timeout"] = timeout

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

        async def get(self, url: str, headers: dict):
            captured["url"] = url
            captured["headers"] = headers
            return FakeResponse()

    monkeypatch.setattr(httpx, "AsyncClient", FakeClient)
    monkeypatch.setattr(parse_service.settings, "master_token", "secret")

    assert await parse_service.download_file("http://java/file") == b"payload"
    assert captured["headers"]["Authorization"] == "Bearer secret"
