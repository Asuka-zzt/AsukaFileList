package com.asuka.filelist.application.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PasswordService BCrypt 行为测试。
 */
class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    /**
     * 密码哈希不能等于明文，且正确密码可验证。
     */
    @Test
    void hash_isIrreversibleAndMatchesRawPassword() {
        String hash = passwordService.hash("strong-password");

        assertThat(hash).isNotEqualTo("strong-password");
        assertThat(passwordService.matches("strong-password", hash)).isTrue();
        assertThat(passwordService.matches("wrong-password", hash)).isFalse();
    }
}
