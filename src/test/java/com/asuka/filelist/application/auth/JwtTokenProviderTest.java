package com.asuka.filelist.application.auth;

import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 签发和解析测试。
 */
class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(properties());

    /**
     * 签发后的 token 能解析出用户 ID、用户名和密码时间戳。
     */
    @Test
    void issueAndParse_returnsPayload() {
        TokenIssue issue = provider.issue(10L, "alice", 123L);

        TokenPayload payload = provider.parse(issue.token());

        assertThat(payload.userId()).isEqualTo(10L);
        assertThat(payload.username()).isEqualTo("alice");
        assertThat(payload.passwordTs()).isEqualTo(123L);
        assertThat(payload.expiresAt()).isEqualTo(issue.expiresAt().truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * 非法 token 会转换为统一业务异常。
     */
    @Test
    void parseInvalidToken_throwsUnauthorized() {
        assertThatThrownBy(() -> provider.parse("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid or expired token");
    }

    /**
     * 构造测试配置。
     */
    private AsukaProperties properties() {
        return new AsukaProperties(
                new AsukaProperties.Jwt("unit-test-secret", 1),
                new AsukaProperties.Ai("http://localhost:8000", "key", "token"),
                new AsukaProperties.Storage(false, false, List.of("/tmp")),
                new AsukaProperties.Upload(
                        AsukaProperties.DataSize.ofMegabytes(100),
                        AsukaProperties.DataSize.ofMegabytes(10)
                ),
                new AsukaProperties.Bootstrap("admin", "password", "guest", "", false)
        );
    }
}
