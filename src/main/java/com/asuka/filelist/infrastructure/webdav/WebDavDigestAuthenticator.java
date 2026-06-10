package com.asuka.filelist.infrastructure.webdav;

import com.asuka.filelist.application.auth.CurrentUserService;
import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.application.webdav.WebdavCredentialService;
import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.domain.user.User;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebDAV HTTP Digest 鉴权（RFC 2617，qop=auth）。
 *
 * <p>选 Digest 让 Windows 在明文 HTTP 上直接挂载（免改注册表、免 TLS）。校验用每用户
 * 存储的 {@code HA1}（{@link WebdavCredentialService}）。nonce 为
 * {@code base64(ts:HMAC(ts))}，限时防伪造；不跟踪 nc（首版「假重放保护」，足够本地/局域网）。
 */
@Component
public class WebDavDigestAuthenticator {

    private static final long NONCE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final Pattern PARAM = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))");

    private final UserApplicationService userApplicationService;
    private final CurrentUserService currentUserService;
    private final byte[] nonceKey;

    public WebDavDigestAuthenticator(UserApplicationService userApplicationService,
                                     CurrentUserService currentUserService,
                                     AsukaProperties properties) {
        this.userApplicationService = userApplicationService;
        this.currentUserService = currentUserService;
        this.nonceKey = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 校验 Authorization: Digest，成功返回当前用户。
     */
    public Optional<CurrentUser> authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Digest ", 0, 7)) {
            return Optional.empty();
        }
        Map<String, String> params = parse(header.substring(7));
        String username = params.get("username");
        String nonce = params.get("nonce");
        String response = params.get("response");
        String uri = params.get("uri");
        if (username == null || nonce == null || response == null || uri == null || !validNonce(nonce)) {
            return Optional.empty();
        }

        User user = userApplicationService.findByUsername(username);
        if (user == null || user.disabled()) {
            return Optional.empty();
        }
        String ha1 = userApplicationService.findWebdavHa1(user.id());
        if (ha1 == null || ha1.isBlank()) {
            return Optional.empty();
        }

        String expected = computeResponse(ha1, request.getMethod(), uri, params);
        if (!constantTimeEquals(expected, response)) {
            return Optional.empty();
        }
        return Optional.of(currentUserService.toCurrentUser(user));
    }

    /**
     * 发送 401 Digest challenge。
     */
    public void sendChallenge(HttpServletResponse response) {
        String challenge = "Digest realm=\"" + WebdavCredentialService.REALM + "\""
                + ", qop=\"auth\""
                + ", nonce=\"" + newNonce() + "\""
                + ", opaque=\"" + md5Hex(WebdavCredentialService.REALM) + "\"";
        response.setHeader("WWW-Authenticate", challenge);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    // ─── Digest 计算 ────────────────────────────────────────────

    /**
     * 按 qop 计算期望 response；qop=auth 用 nc/cnonce，否则退回 RFC 2069。
     */
    private String computeResponse(String ha1, String method, String uri, Map<String, String> params) {
        String ha2 = md5Hex(method + ":" + uri);
        String nonce = params.get("nonce");
        String qop = params.get("qop");
        if (qop != null) {
            String nc = params.getOrDefault("nc", "");
            String cnonce = params.getOrDefault("cnonce", "");
            return md5Hex(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
        }
        return md5Hex(ha1 + ":" + nonce + ":" + ha2);
    }

    // ─── nonce ──────────────────────────────────────────────────

    private String newNonce() {
        long ts = System.currentTimeMillis();
        String payload = ts + ":" + hmac(Long.toString(ts));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private boolean validNonce(String nonce) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(nonce), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                return false;
            }
            String tsPart = decoded.substring(0, colon);
            String mac = decoded.substring(colon + 1);
            if (!constantTimeEquals(hmac(tsPart), mac)) {
                return false;
            }
            long ts = Long.parseLong(tsPart);
            return System.currentTimeMillis() - ts <= NONCE_TTL_MILLIS;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ─── 工具 ───────────────────────────────────────────────────

    private Map<String, String> parse(String header) {
        Map<String, String> result = new HashMap<>();
        Matcher m = PARAM.matcher(header);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3);
            result.put(m.group(1), value);
        }
        return result;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(nonceKey, "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
    }

    private static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
