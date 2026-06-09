package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 创建分享请求。{@code rootPath} 为创建者可见路径；可选密码/过期/次数/开关。
 */
public record ShareCreateRequest(
        @NotBlank String rootPath,
        String name,
        String password,
        LocalDateTime expiresAt,
        Long accessLimit,
        Boolean burnAfterRead,
        Boolean allowPreview,
        Boolean allowDownload
) {
}
