package com.asuka.filelist.application.auth;

import java.time.Instant;

/**
 * 从 JWT 中解析出的可信身份载荷。
 */
public record TokenPayload(
        Long userId,
        String username,
        long passwordTs,
        Instant issuedAt,
        Instant expiresAt
) {
}
