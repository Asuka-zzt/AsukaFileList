package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 公开分享文件详情请求。
 */
public record PublicShareGetRequest(
        @NotBlank String shareId,
        String subPath
) {
}
