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
 * 下载签名服务：HMAC-SHA256 绑定路径与过期时间，供 /d 与 /sd（M7）校验。
 * 签名格式 {@code base64url(hmac(path:expire)) + ":" + expire}，expire=0 表示永不过期。
 * 复用 jwt secret 作为 HMAC 密钥，避免额外配置。
 */
@Service
public class DownloadSignService {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;
    private final long ttlSeconds;
    private final boolean signAll;

    public DownloadSignService(AsukaProperties properties) {
        this.key = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = properties.download().signTtlSeconds();
        this.signAll = properties.download().signAll();
    }

    /**
     * 为路径生成下载签名，按配置 TTL 计算过期时间。
     */
    public String sign(String path) {
        long expire = ttlSeconds > 0 ? Instant.now().getEpochSecond() + ttlSeconds : 0L;
        return hmacBase64(path, expire) + ":" + expire;
    }

    /**
     * 校验签名是否匹配路径且未过期。
     */
    public boolean verify(String path, String sign) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        int idx = sign.lastIndexOf(':');
        if (idx < 0) {
            return false;
        }
        String mac = sign.substring(0, idx);
        long expire;
        try {
            expire = Long.parseLong(sign.substring(idx + 1));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (expire != 0 && Instant.now().getEpochSecond() > expire) {
            return false;
        }
        byte[] expected = hmacBase64(path, expire).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, mac.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 是否对所有文件强制签名。
     */
    public boolean signAll() {
        return signAll;
    }

    /**
     * 计算 path:expire 的 HMAC 并 base64url 编码。
     */
    private String hmacBase64(String path, long expire) {
        try {
            Mac instance = Mac.getInstance(ALGORITHM);
            instance.init(new SecretKeySpec(key, ALGORITHM));
            byte[] raw = instance.doFinal((path + ":" + expire).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to sign download path");
        }
    }
}
