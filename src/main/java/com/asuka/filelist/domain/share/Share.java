package com.asuka.filelist.domain.share;

import java.time.LocalDateTime;

/**
 * 分享领域模型，由 {@code shares} 表映射。
 *
 * <p>{@code rootPath} 为创建者的用户可见路径（相对其 basePath），公开访问时夹在该子树内。
 * 密码以 BCrypt 哈希存于 {@code passwordHash}；{@code accessLimit=0} 表示不限访问次数。
 */
public record Share(
        Long id,
        String shareId,
        Long creatorId,
        String name,
        String rootPath,
        boolean isDir,
        String passwordHash,
        boolean burnAfterRead,
        long accessLimit,
        long accessCount,
        boolean allowPreview,
        boolean allowDownload,
        boolean enabled,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {

    /**
     * 是否设置了访问密码。
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
