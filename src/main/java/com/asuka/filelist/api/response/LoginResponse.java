package com.asuka.filelist.api.response;

import java.time.Instant;

/**
 * 登录成功响应。
 */
public record LoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        CurrentUserResponse user
) {
}
