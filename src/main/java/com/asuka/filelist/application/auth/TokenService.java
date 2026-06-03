package com.asuka.filelist.application.auth;

import com.asuka.filelist.infrastructure.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

/**
 * 认证层 token 门面，隔离 JWT 具体实现。
 */
@Service
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;

    public TokenService(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 签发用户访问 token。
     */
    public TokenIssue issue(Long userId, String username, long passwordTs) {
        return jwtTokenProvider.issue(userId, username, passwordTs);
    }

    /**
     * 解析并校验访问 token。
     */
    public TokenPayload parse(String token) {
        return jwtTokenProvider.parse(token);
    }
}
