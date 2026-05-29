"""RAG 流式问答服务（SSE）"""
import httpx
import json
from typing import AsyncIterator
from app.core.config import settings
from app.services.search_service import semantic_search

SYSTEM_PROMPT = (
    "你是一个智能文件助手，根据用户的网盘文档回答问题。"
    "只使用提供的上下文内容作答，若上下文中没有相关信息，请如实说明。"
    "回答语言与用户提问语言保持一致。"
)


async def rag_chat_stream(
    user_id: int,
    question: str,
    history: list[dict],
    top_k: int = 5,
) -> AsyncIterator[str]:
    """
    RAG 流式问答：
    1. 语义检索最相关文档 chunk
    2. 拼装 prompt 调用 DeepSeek chat（stream=True）
    3. 以 SSE 格式逐 token yield
    """
    # 检索上下文
    context_docs = await semantic_search(user_id, question, top_k)
    context = "\n\n".join(
        f"[文档片段 {i+1}]\n{doc['content']}"
        for i, doc in enumerate(context_docs)
    )

    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    # 加入历史对话（最多保留最近 6 轮）
    messages.extend(history[-12:])
    messages.append({
        "role": "user",
        "content": f"参考以下文档内容：\n{context}\n\n问题：{question}"
    })

    async with httpx.AsyncClient(timeout=120) as client:
        async with client.stream(
            "POST",
            f"{settings.deepseek_base_url}/chat/completions",
            headers={"Authorization": f"Bearer {settings.deepseek_api_key}"},
            json={
                "model": settings.deepseek_chat_model,
                "messages": messages,
                "stream": True,
                "temperature": 0.7,
            },
        ) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                if not line.startswith("data: "):
                    continue
                payload = line[6:].strip()
                if payload == "[DONE]":
                    yield json.dumps({"type": "done"})
                    return
                try:
                    chunk = json.loads(payload)
                    delta = chunk["choices"][0]["delta"].get("content", "")
                    if delta:
                        yield json.dumps({"type": "token", "content": delta})
                except (json.JSONDecodeError, KeyError, IndexError):
                    continue
