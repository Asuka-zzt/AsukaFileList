package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 公开分享密码校验请求。
 */
public record ShareAuthRequest(
        @NotBlank String shareId,
        String password
) {
}
