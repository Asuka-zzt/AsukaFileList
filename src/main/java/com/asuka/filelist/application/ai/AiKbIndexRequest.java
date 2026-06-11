package com.asuka.filelist.application.ai;

/**
 * 提交知识库文档索引的请求（Java → AI 服务 POST /kb/{kbId}/index）。
 *
 * @param docId           Java 分配的稳定文档 id（作为 LightRAG doc_id）
 * @param fileDownloadUrl 带短期签名的内部下载 URL
 * @param mimeType        文件 MIME
 * @param fileName        文件名（作为 LightRAG file_path 来源）
 * @param docType         paper | book | note
 */
public record AiKbIndexRequest(
        String docId,
        String fileDownloadUrl,
        String mimeType,
        String fileName,
        String docType
) {
}
