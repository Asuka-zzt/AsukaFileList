"""P1 冒烟脚本：对一个临时 workspace 完成 ainsert → aquery，并核对 PG 落库。

运行（在 ai-service 容器内，连到 compose 网络的 postgres）：
    python -m scripts.smoke_lightrag

依赖环境变量：DEEPSEEK_API_KEY、POSTGRES_AGE_DSN（或 POSTGRES_*）、HF_HUB_OFFLINE=1。
"""
import asyncio
import os
import re

from app.services import lightrag_service as lr

KB_ID = os.getenv("SMOKE_KB_ID", "smoke1")
SAMPLE = (
    "Marie Curie was a physicist and chemist who conducted pioneering research on "
    "radioactivity. She was the first woman to win a Nobel Prize, and the only person to "
    "win Nobel Prizes in two sciences (Physics and Chemistry). She discovered the elements "
    "polonium and radium, working with her husband Pierre Curie in Paris."
)


async def _verify_pg(workspace: str) -> None:
    """直接连 PG，确认该 workspace 已落 chunk / 实体向量 / AGE 图节点。"""
    import asyncpg

    lr._ensure_pg_env()
    conn = await asyncpg.connect(
        host=os.environ["POSTGRES_HOST"],
        port=int(os.environ["POSTGRES_PORT"]),
        user=os.environ["POSTGRES_USER"],
        password=os.environ.get("POSTGRES_PASSWORD"),
        database=os.environ["POSTGRES_DATABASE"],
    )
    try:
        chunks = await conn.fetchval(
            "SELECT count(*) FROM lightrag_doc_chunks WHERE workspace=$1", workspace
        )
        entities = await conn.fetchval(
            "SELECT count(*) FROM lightrag_vdb_entity WHERE workspace=$1", workspace
        )
        # AGE 图节点数（图名 = {workspace}_chunk_entity_relation）
        graph = re.sub(r"[^a-zA-Z0-9_]", "_", workspace) + "_chunk_entity_relation"
        await conn.execute("LOAD 'age'; SET search_path = ag_catalog, public;")
        nodes = await conn.fetchval(
            f"SELECT count(*) FROM cypher('{graph}', $$ MATCH (n) RETURN n $$) AS (n agtype);"
        )
        print(f"[smoke] PG: doc_chunks={chunks}, vdb_entity={entities}, graph_nodes={nodes}")
        assert chunks and chunks > 0, "no chunks persisted"
        assert entities and entities > 0, "no entity vectors persisted"
        assert nodes and nodes > 0, "no graph nodes persisted"
        print("[smoke] PG verification PASSED")
    finally:
        await conn.close()


async def main() -> None:
    workspace = lr.workspace_of(KB_ID)
    print(f"[smoke] workspace = {workspace}")

    print("[smoke] inserting sample text ...")
    await lr.ainsert(KB_ID, SAMPLE, ids=[f"{KB_ID}-doc1"], file_paths=["marie_curie.txt"])
    print("[smoke] insert done")

    question = "Who is Marie Curie and what did she discover?"
    print(f"[smoke] querying (mix): {question}")
    answer = await lr.aquery(KB_ID, question, mode="mix")
    print("[smoke] answer:\n" + (answer[:600] if answer else "<empty>"))

    await _verify_pg(workspace)
    await lr.close_all()
    print("[smoke] DONE")


if __name__ == "__main__":
    asyncio.run(main())
