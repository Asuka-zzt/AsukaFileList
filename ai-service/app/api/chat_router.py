"""RAG 流式问答接口（SSE）"""
from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from app.core.security import verify_api_key
from app.services.chat_service import rag_chat_stream

router = APIRouter(dependencies=[Depends(verify_api_key)])


class ChatRequest(BaseModel):
    userId:    int
    sessionId: int = 0
    question:  str
    history:   list[dict] = []


@router.post("/chat")
async def api_chat(req: ChatRequest):
    """SSE 流式响应，Content-Type: text/event-stream"""
    async def event_generator():
        async for token in rag_chat_stream(req.userId, req.question, req.history):
            yield f"data: {token}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")
