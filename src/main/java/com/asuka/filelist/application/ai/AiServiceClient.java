package com.asuka.filelist.application.ai;

import java.io.OutputStream;

/**
 * AI 服务（Python，内网）客户端。封装知识库索引、删除、任务查询与问答透传。
 */
public interface AiServiceClient {

    /** 提交知识库文档的解析+增量索引任务，返回 taskId。 */
    AiKbTaskResponse submitKbIndex(long kbId, AiKbIndexRequest request);

    /** 删除整个知识库的 LightRAG workspace。 */
    void deleteKb(long kbId);

    /** 按 LightRAG doc_id 删除知识库内某文档的索引。 */
    void deleteKbDocument(long kbId, String docId);

    /** 查询索引任务状态。 */
    AiKbTaskResponse getKbTask(String taskId);

    /**
     * 透传知识库问答的 SSE 流到给定输出流，阻塞直到流结束。
     * docId 非空时走单文档过滤问答。
     */
    void streamChat(long kbId, AiKbChatRequest request, OutputStream out);
}
