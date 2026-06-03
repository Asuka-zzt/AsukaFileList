package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求参数。
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
