"""文档解析服务：把网盘文件统一解析为可入库的 Markdown 文本。

- PDF（论文 / 书籍 / 含表格）→ opendataloader-pdf 转结构化 Markdown（容器内 JRE）。
- Markdown 笔记 → 直接读取。
- 其它类型暂不支持，抛 `ParseError` 供上层把任务标 `failed`。

文件下载复用 Java 内部下载 URL（短期签名，`Authorization: Bearer <master_token>`）。
"""
import asyncio
import glob
import os
import tempfile

import httpx
import opendataloader_pdf

from app.core.config import settings


class ParseError(Exception):
    """解析（下载 / 转换）失败。调用方据此把索引任务标记为 failed。"""


async def download_file(url: str) -> bytes:
    """从 Java 内部下载 URL 获取文件字节（短期签名 + 内部 token）。"""
    try:
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.get(
                url, headers={"Authorization": f"Bearer {settings.master_token}"}
            )
            resp.raise_for_status()
            return resp.content
    except httpx.HTTPError as e:
        raise ParseError(f"download failed: {e}") from e


def _is_pdf(mime_type: str | None, file_name: str | None) -> bool:
    return "pdf" in (mime_type or "").lower() or (file_name or "").lower().endswith(".pdf")


def _is_markdown(mime_type: str | None, file_name: str | None) -> bool:
    name = (file_name or "").lower()
    return name.endswith((".md", ".markdown")) or "markdown" in (mime_type or "").lower()


def parse_pdf_bytes(data: bytes, file_name: str, password: str | None = None) -> str:
    """PDF 字节 → 结构化 Markdown 文本。

    opendataloader-pdf 需要文件路径并把结果写到 output_dir，这里用临时目录中转，
    转换后读取产出的 .md。`image_output=off` 仅取文本、不抽图。
    """
    with tempfile.TemporaryDirectory() as work:
        pdf_path = os.path.join(work, "input.pdf")
        with open(pdf_path, "wb") as fh:
            fh.write(data)
        out_dir = os.path.join(work, "out")
        os.makedirs(out_dir, exist_ok=True)
        try:
            opendataloader_pdf.convert(
                input_path=pdf_path,
                output_dir=out_dir,
                format=["markdown"],
                image_output="off",
                quiet=True,
            )
        except Exception as e:  # 子进程 / JVM 失败统一转 ParseError
            raise ParseError(f"opendataloader convert failed for {file_name}: {e}") from e

        md_files = sorted(glob.glob(os.path.join(out_dir, "**", "*.md"), recursive=True))
        if not md_files:
            raise ParseError(f"no markdown produced for {file_name}")
        text = "\n\n".join(
            open(p, encoding="utf-8", errors="replace").read() for p in md_files
        )
        if not text.strip():
            raise ParseError(f"empty markdown for {file_name}")
        return text


def parse_markdown_bytes(data: bytes) -> str:
    """Markdown 笔记字节 → 文本（UTF-8，容错解码）。"""
    text = data.decode("utf-8", errors="replace")
    if not text.strip():
        raise ParseError("empty markdown file")
    return text


async def parse_document(
    file_download_url: str,
    mime_type: str | None,
    file_name: str | None,
    password: str | None = None,
) -> str:
    """下载并解析为 Markdown 文本。

    PDF 走 opendataloader（阻塞的 JVM 子进程，丢线程池避免阻塞事件循环）；
    Markdown 直读；其余类型抛 `ParseError`。
    """
    data = await download_file(file_download_url)
    if _is_markdown(mime_type, file_name):
        return parse_markdown_bytes(data)
    if _is_pdf(mime_type, file_name):
        loop = asyncio.get_running_loop()
        return await loop.run_in_executor(
            None, lambda: parse_pdf_bytes(data, file_name or "input.pdf", password)
        )
    raise ParseError(f"unsupported file type: mime={mime_type}, name={file_name}")
