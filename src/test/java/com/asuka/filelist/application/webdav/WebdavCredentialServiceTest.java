package com.asuka.filelist.application.webdav;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebDAV HA1 计算：与 RFC 2617 的 MD5(username:realm:password) 一致且确定。
 */
class WebdavCredentialServiceTest {

    /**
     * 已知向量：MD5("alice:AsukaFileList:wonderland")。
     */
    @Test
    void ha1_matchesKnownVector() {
        assertThat(WebdavCredentialService.ha1("alice", "wonderland"))
                .isEqualTo("717a115520dd23e8b0698c7e468822de");
    }

    /**
     * 32 位小写十六进制、确定性、随用户名/密码变化。
     */
    @Test
    void ha1_isDeterministicHex() {
        String a = WebdavCredentialService.ha1("bob", "pw");
        assertThat(a).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(WebdavCredentialService.ha1("bob", "pw")).isEqualTo(a);
        assertThat(WebdavCredentialService.ha1("bob", "other")).isNotEqualTo(a);
        assertThat(WebdavCredentialService.ha1("carol", "pw")).isNotEqualTo(a);
    }
}
