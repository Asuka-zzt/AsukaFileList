package com.asuka.filelist.infrastructure.security;

import com.asuka.filelist.common.config.AsukaProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DownloadSignService 签名生成与校验单元测试。
 */
class DownloadSignServiceTest {

    private static final String SECRET = "unit-test-secret";

    private final DownloadSignService service = new DownloadSignService(properties(14400, false));

    /**
     * 同路径签名可被校验通过。
     */
    @Test
    void signAndVerify_roundTrips() {
        String sign = service.sign("/a/file.txt");
        assertThat(service.verify("/a/file.txt", sign)).isTrue();
    }

    /**
     * 路径不匹配的签名校验失败。
     */
    @Test
    void verify_rejectsPathMismatch() {
        String sign = service.sign("/a/file.txt");
        assertThat(service.verify("/a/other.txt", sign)).isFalse();
    }

    /**
     * 篡改或格式非法的签名校验失败。
     */
    @Test
    void verify_rejectsTamperedOrMalformed() {
        assertThat(service.verify("/a/file.txt", "garbage")).isFalse();
        assertThat(service.verify("/a/file.txt", "abc:notnumber")).isFalse();
        assertThat(service.verify("/a/file.txt", null)).isFalse();
    }

    /**
     * 过期时间已过的签名校验失败，未过期通过（用相同算法构造）。
     */
    @Test
    void verify_respectsExpiry() throws Exception {
        long now = Instant.now().getEpochSecond();
        assertThat(service.verify("/a/file.txt", craft("/a/file.txt", now - 60))).isFalse();
        assertThat(service.verify("/a/file.txt", craft("/a/file.txt", now + 600))).isTrue();
    }

    /**
     * 用与服务相同的密钥与算法构造签名，用于过期校验。
     */
    private String craft(String path, long expire) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((path + ":" + expire).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw) + ":" + expire;
    }

    /**
     * 构造测试配置。
     */
    private AsukaProperties properties(long ttlSeconds, boolean signAll) {
        return new AsukaProperties(
                new AsukaProperties.Jwt(SECRET, 1),
                new AsukaProperties.Ai("http://localhost:8000", "key", "token", "http://localhost:8080"),
                new AsukaProperties.Storage(false, false, List.of("/tmp")),
                new AsukaProperties.Upload(
                        AsukaProperties.DataSize.ofMegabytes(100),
                        AsukaProperties.DataSize.ofMegabytes(10)
                ),
                new AsukaProperties.Bootstrap("admin", "password", "guest", "", false),
                new AsukaProperties.Download(ttlSeconds, signAll),
                new AsukaProperties.Share(7200)
        );
    }
}
