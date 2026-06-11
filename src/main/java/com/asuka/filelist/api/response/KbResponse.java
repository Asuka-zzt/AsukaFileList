package com.asuka.filelist.api.response;

import com.asuka.filelist.infrastructure.persistence.entity.KbKnowledgeBaseEntity;

import java.time.LocalDateTime;

/** 知识库信息响应。 */
public record KbResponse(
        Long id,
        String name,
        String description,
        String workspace,
        String status,
        LocalDateTime createdAt
) {

    public static KbResponse of(KbKnowledgeBaseEntity e) {
        return new KbResponse(e.getId(), e.getName(), e.getDescription(),
                e.getWorkspace(), e.getStatus(), e.getCreatedAt());
    }
}
