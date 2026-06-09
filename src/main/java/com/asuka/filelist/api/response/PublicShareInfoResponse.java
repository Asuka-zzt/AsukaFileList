package com.asuka.filelist.api.response;

/**
 * 公开分享元信息（匿名可见，不含敏感字段）。
 */
public record PublicShareInfoResponse(
        String shareId,
        String name,
        boolean isDir,
        boolean needPassword,
        boolean allowPreview,
        boolean allowDownload
) {
}
