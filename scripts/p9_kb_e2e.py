#!/usr/bin/env python3
"""P9 Graph RAG knowledge-base end-to-end acceptance test.

The script only calls the Java public API. Java is responsible for ownership
checks and proxying requests to the internal Python AI service.
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


BASE_URL = os.getenv("P9_BASE_URL", "http://localhost:8080").rstrip("/")
USERNAME = os.getenv("P9_ADMIN_USERNAME", "admin")
PASSWORD = os.getenv("P9_ADMIN_PASSWORD", "")
LOCAL_ROOT = Path(os.getenv("P9_LOCAL_ROOT", "/tmp/asuka-file-list/p9-e2e"))
EXISTING_MOUNT = os.getenv("P9_STORAGE_MOUNT", "").strip()
INDEX_TIMEOUT_S = int(os.getenv("P9_INDEX_TIMEOUT_S", "900"))
REQUEST_TIMEOUT_S = int(os.getenv("P9_REQUEST_TIMEOUT_S", "300"))


class AcceptanceError(RuntimeError):
    """Raised when an acceptance step returns an invalid result."""


def _request(
    method: str,
    path: str,
    token: str | None = None,
    payload: Any = None,
    body: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = REQUEST_TIMEOUT_S,
) -> urllib.response.addinfourl:
    """Send one HTTP request and convert non-2xx responses to readable errors."""
    request_headers = dict(headers or {})
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    request_body = body
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
        request_body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        BASE_URL + path,
        data=request_body,
        headers=request_headers,
        method=method,
    )
    try:
        return urllib.request.urlopen(request, timeout=timeout)
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise AcceptanceError(
            f"{method} {path} failed: HTTP {exc.code}: {error_body}"
        ) from exc
    except urllib.error.URLError as exc:
        raise AcceptanceError(f"{method} {path} failed: {exc}") from exc


def _request_json(
    method: str,
    path: str,
    token: str | None = None,
    payload: Any = None,
    body: bytes | None = None,
    headers: dict[str, str] | None = None,
) -> Any:
    """Send a request and unwrap the Java ApiResponse data field."""
    with _request(method, path, token, payload, body, headers) as response:
        raw = response.read().decode("utf-8")
    parsed = json.loads(raw)
    if not parsed.get("success"):
        raise AcceptanceError(
            f"{method} {path} returned {parsed.get('code')}: {parsed.get('message')}"
        )
    return parsed.get("data")


def _stream_chat(
    path: str,
    token: str,
    question: str,
) -> list[dict[str, Any]]:
    """Consume a Java-proxied SSE response and return parsed data events."""
    with _request(
        "POST",
        path,
        token,
        payload={"question": question, "history": []},
        timeout=max(REQUEST_TIMEOUT_S, 360),
    ) as response:
        content_type = response.headers.get("Content-Type", "")
        if not content_type.startswith("text/event-stream"):
            raise AcceptanceError(f"{path} returned unexpected Content-Type {content_type}")
        events: list[dict[str, Any]] = []
        data_lines: list[str] = []
        for raw_line in response:
            line = raw_line.decode("utf-8", errors="replace").rstrip("\r\n")
            if not line:
                if data_lines:
                    events.append(json.loads("\n".join(data_lines)))
                    data_lines.clear()
                continue
            if line.startswith("data:"):
                data_lines.append(line[5:].strip())
        if data_lines:
            events.append(json.loads("\n".join(data_lines)))
    return events


def _assert_chat(events: list[dict[str, Any]], expected_file: str) -> None:
    """Require answer tokens, same-document citations, and a done event."""
    errors = [event for event in events if event.get("type") == "error"]
    if errors:
        raise AcceptanceError(f"chat returned error events: {errors}")
    tokens = [event.get("text", "") for event in events if event.get("type") == "token"]
    if not "".join(tokens).strip():
        raise AcceptanceError("chat returned no answer tokens")
    citation_events = [
        event for event in events if event.get("type") == "citations"
    ]
    citations = citation_events[-1].get("items", []) if citation_events else []
    if not citations:
        raise AcceptanceError("chat returned no citations")
    wrong_sources = [
        item for item in citations
        if not str(item.get("file_path", "")).endswith(expected_file)
    ]
    if wrong_sources:
        raise AcceptanceError(f"chat citations contain unexpected sources: {wrong_sources}")
    if not any(event.get("type") == "done" for event in events):
        raise AcceptanceError("chat stream did not finish with a done event")


def _login() -> str:
    """Authenticate the configured administrator and return its access token."""
    if not PASSWORD:
        raise AcceptanceError("P9_ADMIN_PASSWORD is required")
    data = _request_json(
        "POST",
        "/api/auth/login",
        payload={"username": USERNAME, "password": PASSWORD},
    )
    return data["accessToken"]


def _create_storage(token: str, mount_path: str) -> int:
    """Create a temporary Local storage mount for the acceptance document."""
    LOCAL_ROOT.mkdir(parents=True, exist_ok=True)
    data = _request_json(
        "POST",
        "/api/admin/storage/create",
        token,
        payload={
            "mountPath": mount_path,
            "driver": "Local",
            "addition": {"rootPath": str(LOCAL_ROOT)},
            "disabled": False,
            "remark": "P9 acceptance temporary storage",
        },
    )
    return int(data["id"])


def _poll_indexed(token: str, kb_id: int, doc_id: int) -> dict[str, Any]:
    """Poll the Java document status until indexed, failed, or timed out."""
    deadline = time.monotonic() + INDEX_TIMEOUT_S
    last_status = "pending"
    while time.monotonic() < deadline:
        documents = _request_json(
            "GET", f"/api/kb/{kb_id}/documents", token
        )
        document = next(
            (item for item in documents if int(item["id"]) == doc_id),
            None,
        )
        if document is None:
            raise AcceptanceError("indexed document disappeared from the knowledge base")
        last_status = document["status"]
        print(f"[P9] index status: {last_status}", flush=True)
        if last_status == "indexed":
            return document
        if last_status == "failed":
            raise AcceptanceError(
                f"index failed: {document.get('errorMsg') or 'unknown error'}"
            )
        time.sleep(3)
    raise AcceptanceError(
        f"index did not finish within {INDEX_TIMEOUT_S}s; last status={last_status}"
    )


def _delete_document(token: str, kb_id: int, doc_id: int) -> None:
    """Delete an indexed document and verify its public record is gone."""
    _request_json("DELETE", f"/api/kb/{kb_id}/documents/{doc_id}", token)
    documents = _request_json("GET", f"/api/kb/{kb_id}/documents", token)
    if any(int(item["id"]) == doc_id for item in documents):
        raise AcceptanceError("deleted document remains visible in the knowledge base")


def _best_effort(method: str, path: str, token: str, payload: Any = None) -> None:
    """Run cleanup without hiding the original acceptance failure."""
    try:
        _request_json(method, path, token, payload=payload)
    except Exception as exc:  # noqa: BLE001 - cleanup must continue
        print(f"[P9] cleanup warning: {exc}", file=sys.stderr)


def run() -> None:
    """Execute the full knowledge-base lifecycle."""
    stamp = int(time.time())
    mount_path = EXISTING_MOUNT.rstrip("/") or f"/p9-e2e-{stamp}"
    file_name = f"p9-note-{stamp}.md"
    file_path = f"{mount_path}/{file_name}"
    storage_id: int | None = None
    kb_id: int | None = None
    doc_id: int | None = None
    token = _login()

    try:
        _request_json("GET", "/api/health")
        if not EXISTING_MOUNT:
            storage_id = _create_storage(token, mount_path)
            print(f"[P9] created temporary storage {storage_id} at {mount_path}")

        content = (
            "# P9 Acceptance\n\n"
            "AsukaFileList combines mounted file storage with Graph RAG knowledge bases. "
            "The P9 milestone verifies indexing, whole-library QA, document QA, and citations."
        ).encode("utf-8")
        _request_json(
            "PUT",
            "/api/fs/put",
            token,
            body=content,
            headers={"File-Path": file_path, "Content-Type": "text/markdown"},
        )
        print(f"[P9] uploaded {file_path}")

        kb = _request_json(
            "POST",
            "/api/kb",
            token,
            payload={"name": f"P9 Acceptance {stamp}", "description": "temporary"},
        )
        kb_id = int(kb["id"])
        document = _request_json(
            "POST",
            f"/api/kb/{kb_id}/documents",
            token,
            payload={"path": file_path, "docType": "note"},
        )
        doc_id = int(document["id"])
        _poll_indexed(token, kb_id, doc_id)

        question = "What capabilities and milestone does this document describe?"
        whole_events = _stream_chat(f"/api/kb/{kb_id}/chat", token, question)
        _assert_chat(whole_events, file_name)
        document_events = _stream_chat(
            f"/api/kb/{kb_id}/documents/{doc_id}/chat", token, question
        )
        _assert_chat(document_events, file_name)
        print("[P9] whole-library and single-document QA passed")
        _delete_document(token, kb_id, doc_id)
        doc_id = None
        print("[P9] indexed document deletion passed")
    finally:
        if token and kb_id is not None and doc_id is not None:
            _best_effort(
                "DELETE", f"/api/kb/{kb_id}/documents/{doc_id}", token
            )
        if token and kb_id is not None:
            _best_effort("DELETE", f"/api/kb/{kb_id}", token)
        if token:
            directory, name = file_path.rsplit("/", 1)
            _best_effort(
                "POST",
                "/api/fs/remove",
                token,
                payload={"dir": directory or "/", "names": [name]},
            )
        if token and storage_id is not None:
            _best_effort(
                "POST",
                "/api/admin/storage/delete",
                token,
                payload={"id": storage_id},
            )
        if storage_id is not None:
            try:
                LOCAL_ROOT.rmdir()
            except OSError:
                pass


if __name__ == "__main__":
    try:
        run()
    except AcceptanceError as exc:
        print(f"[P9] FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
    print("[P9] PASSED")
