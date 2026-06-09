package com.asuka.filelist.api.response;

import java.time.LocalDateTime;

/**
 * 分享管理响应（创建者视角，不含密码哈希）。
 */
public record ShareResponse(
        Long id,
        String shareId,
        String name,
        String rootPath,
        boolean isDir,
        boolean hasPassword,
        boolean burnAfterRead,
        long accessLimit,
        long accessCount,
        boolean allowPreview,
        boolean allowDownload,
        boolean enabled,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
