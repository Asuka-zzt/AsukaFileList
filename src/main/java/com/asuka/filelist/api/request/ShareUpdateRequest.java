package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 更新分享请求。可空字段表示"保持不变"；{@code password} 为 null 保持、空串清除、非空设置。
 */
public record ShareUpdateRequest(
        @NotNull Long id,
        String name,
        String password,
        LocalDateTime expiresAt,
        Long accessLimit,
        Boolean burnAfterRead,
        Boolean allowPreview,
        Boolean allowDownload,
        Boolean enabled
) {
}
