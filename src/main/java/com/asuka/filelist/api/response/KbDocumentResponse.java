package com.asuka.filelist.api.response;

import com.asuka.filelist.infrastructure.persistence.entity.KbDocumentEntity;

import java.time.LocalDateTime;

/** 知识库文档信息与索引状态响应。 */
public record KbDocumentResponse(
        Long id,
        Long kbId,
        String fileName,
        String sourcePath,
        String docType,
        String status,
        String errorMsg,
        String taskId,
        LocalDateTime createdAt
) {

    public static KbDocumentResponse of(KbDocumentEntity e) {
        return new KbDocumentResponse(e.getId(), e.getKbId(), e.getFileName(), e.getSourcePath(),
                e.getDocType(), e.getStatus(), e.getErrorMsg(), e.getTaskId(), e.getCreatedAt());
    }
}
