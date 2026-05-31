package com.asuka.filelist.application.auth;

import java.time.Instant;

/**
 * JWT 签发结果。
 */
public record TokenIssue(
        String token,
        Instant expiresAt
) {
}
