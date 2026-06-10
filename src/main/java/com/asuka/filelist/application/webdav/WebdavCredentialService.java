package com.asuka.filelist.application.webdav;

import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * WebDAV 专用密码管理：用户密码只存 BCrypt 哈希无法用于 Digest，故为 WebDAV 单独存
 * {@code HA1 = MD5(username:realm:password)}（不可逆，不存明文）。
 *
 * <p>{@link #REALM} 固定为常量：HA1 依赖 realm，若可配则改 realm 会静默作废所有已存密码。
 */
@Service
public class WebdavCredentialService {

    /** Digest realm，固定常量（变更将作废所有已存 WebDAV 密码）。 */
    public static final String REALM = "AsukaFileList";

    private final UserApplicationService userApplicationService;

    public WebdavCredentialService(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    /**
     * 设置当前用户的 WebDAV 密码（存 HA1）。
     */
    public void setPassword(CurrentUser currentUser, String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "WebDAV password cannot be empty");
        }
        userApplicationService.updateWebdavHa1(currentUser.id(), ha1(currentUser.username(), password));
    }

    /**
     * 清除当前用户的 WebDAV 密码（禁用 WebDAV 登录）。
     */
    public void clearPassword(CurrentUser currentUser) {
        userApplicationService.updateWebdavHa1(currentUser.id(), null);
    }

    /**
     * 计算 Digest HA1 = MD5(username:realm:password) 的十六进制串。
     */
    public static String ha1(String username, String password) {
        return md5Hex(username + ":" + REALM + ":" + password);
    }

    /**
     * 十六进制 MD5。
     */
    static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MD5 unavailable");
        }
    }
}
