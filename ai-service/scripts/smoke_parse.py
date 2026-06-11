"""P2 冒烟：对样例 PDF / MD 跑解析，打印产出 Markdown 的长度与片段供人工抽检。

用法（容器内，需 JRE）：
    python -m scripts.smoke_parse <file1> <file2> ...
"""
import sys

from app.services import parse_service as ps


def run_one(path: str) -> None:
    name = path.rsplit("/", 1)[-1]
    with open(path, "rb") as fh:
        data = fh.read()
    print(f"\n===== {name} ({len(data)} bytes) =====")
    try:
        if ps._is_markdown(None, name):
            md = ps.parse_markdown_bytes(data)
        else:
            md = ps.parse_pdf_bytes(data, name)
    except Exception as e:  # noqa: BLE001 — 冒烟脚本，打印任何失败
        print(f"[FAIL] {type(e).__name__}: {e}")
        return
    print(f"[ok] markdown chars={len(md)}  table_pipes={md.count('|')}  headings={md.count(chr(10) + '#')}")
    print("---- head(800) ----")
    print(md[:800])
    print("---- tail(300) ----")
    print(md[-300:])


if __name__ == "__main__":
    for p in sys.argv[1:]:
        run_one(p)
