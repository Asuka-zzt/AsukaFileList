package com.asuka.filelist.application.ai;

import java.util.List;

/**
 * 知识库问答请求（Java → AI 服务 POST /kb/{kbId}/chat，SSE）。
 *
 * @param question 用户问题
 * @param docId    非空时走单文档过滤问答；为空时整库问答
 * @param history  历史消息（role/content），可空
 */
public record AiKbChatRequest(
        String question,
        String docId,
        List<AiKbChatMessage> history
) {

    /** 单条对话历史。 */
    public record AiKbChatMessage(String role, String content) {
    }
}
