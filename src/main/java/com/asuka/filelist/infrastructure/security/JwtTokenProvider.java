package com.asuka.filelist.infrastructure.security;

import com.asuka.filelist.application.auth.TokenIssue;
import com.asuka.filelist.application.auth.TokenPayload;
import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * 基于 HS256 的 JWT 签发与校验实现。
 */
@Component
public class JwtTokenProvider {

    private static final String PASSWORD_TS_CLAIM = "pwdTs";

    private final AsukaProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenProvider(AsukaProperties properties) {
        this.properties = properties;
        SecretKeySpec secretKey = createSecretKey(properties.jwt().secret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        this.decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * 签发访问 token。
     */
    public TokenIssue issue(Long userId, String username, long passwordTs) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.jwt().tokenExpiresHours() * 3600L);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("username", username)
                .claim(PASSWORD_TS_CLAIM, passwordTs)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenIssue(token, expiresAt);
    }

    /**
     * 校验并解析访问 token。
     */
    public TokenPayload parse(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return new TokenPayload(
                    Long.parseLong(jwt.getSubject()),
                    jwt.getClaimAsString("username"),
                    readLongClaim(jwt, PASSWORD_TS_CLAIM),
                    jwt.getIssuedAt(),
                    jwt.getExpiresAt()
            );
        } catch (JwtException | NumberFormatException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    /**
     * 将任意长度配置密钥稳定派生为 HS256 所需的 256-bit key。
     */
    private SecretKeySpec createSecretKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "HmacSHA256");
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 algorithm is unavailable");
        }
    }

    /**
     * 兼容 JSON number 反序列化后的不同整数类型。
     */
    private long readLongClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token claim: " + claimName);
    }
}
