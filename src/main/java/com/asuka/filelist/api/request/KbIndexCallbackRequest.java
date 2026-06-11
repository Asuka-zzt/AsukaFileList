package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 服务索引状态回调（POST /internal/kb/index-callback）。
 *
 * @param docId         LightRAG docId（= kb_document.lightrag_doc_id）
 * @param status        pending | parsing | indexing | indexed | failed
 * @param lightragDocId 索引成功回填的 doc_id（可空）
 * @param error         失败原因（可空）
 */
public record KbIndexCallbackRequest(
        @NotBlank String docId,
        @NotBlank String status,
        String lightragDocId,
        String error
) {
}
