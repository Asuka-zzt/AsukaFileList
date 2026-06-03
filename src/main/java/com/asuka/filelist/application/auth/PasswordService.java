package com.asuka.filelist.application.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 密码哈希服务，使用 BCrypt 保存不可逆密码摘要。
 */
@Service
public class PasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成 BCrypt 密码哈希。
     */
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 验证明文密码与 BCrypt 哈希是否匹配。
     */
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }

    /**
     * 生成兼容 users.password_salt 字段的审计盐。
     */
    public String generateAuditSalt() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
