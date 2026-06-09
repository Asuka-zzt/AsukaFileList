package com.asuka.filelist.api.response;

/**
 * 分享密码校验结果：返回访问令牌与基本展示信息。
 */
public record ShareAuthResponse(
        String token,
        String name,
        boolean isDir,
        boolean allowPreview,
        boolean allowDownload
) {
}
