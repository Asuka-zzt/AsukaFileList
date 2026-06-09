package com.asuka.filelist.api.response;

import java.time.LocalDateTime;

/**
 * 任务详情响应。
 */
public record TaskResponse(
        Long id,
        String type,
        String status,
        int progress,
        Long creatorId,
        String result,
        String error,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
