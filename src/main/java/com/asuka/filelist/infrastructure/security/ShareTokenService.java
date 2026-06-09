package com.asuka.filelist.infrastructure.security;

import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 分享访问令牌服务（M7）：HMAC-SHA256 绑定 shareId 与过期时间，密码校验后签发。
 *
 * <p>令牌格式 {@code base64url(hmac(shareId:expire)) + ":" + expire}，复用 jwt secret 作 HMAC 密钥，
 * 与 {@link DownloadSignService} 同款机制，避免额外的会话存储。
 */
@Service
public class ShareTokenService {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;
    private final long ttlSeconds;

    public ShareTokenService(AsukaProperties properties) {
        this.key = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = properties.share().tokenTtlSeconds();
    }

    /**
     * 为 shareId 签发访问令牌，按配置 TTL 计算过期时间。
     */
    public String issue(String shareId) {
        long expire = ttlSeconds > 0 ? Instant.now().getEpochSecond() + ttlSeconds : 0L;
        return hmacBase64(shareId, expire) + ":" + expire;
    }

    /**
     * 校验令牌是否匹配 shareId 且未过期。
     */
    public boolean verify(String shareId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        int idx = token.lastIndexOf(':');
        if (idx < 0) {
            return false;
        }
        String mac = token.substring(0, idx);
        long expire;
        try {
            expire = Long.parseLong(token.substring(idx + 1));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (expire != 0 && Instant.now().getEpochSecond() > expire) {
            return false;
        }
        byte[] expected = hmacBase64(shareId, expire).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, mac.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 shareId:expire 的 HMAC 并 base64url 编码。
     */
    private String hmacBase64(String shareId, long expire) {
        try {
            Mac instance = Mac.getInstance(ALGORITHM);
            instance.init(new SecretKeySpec(key, ALGORITHM));
            byte[] raw = instance.doFinal((shareId + ":" + expire).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to issue share token");
        }
    }
}
