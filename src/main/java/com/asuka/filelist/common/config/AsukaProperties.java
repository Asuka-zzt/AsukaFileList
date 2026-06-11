package com.asuka.filelist.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "asuka")
public record AsukaProperties(
        Jwt jwt,
        Ai ai,
        Storage storage,
        Upload upload,
        Bootstrap bootstrap,
        Download download,
        Share share
) {

    public record Jwt(
            @NotBlank String secret,
            @Positive long tokenExpiresHours
    ) {
    }

    public record Ai(
            @NotBlank String baseUrl,
            @NotBlank String apiKey,
            @NotBlank String internalDownloadToken,
            // 供 AI 服务回连 Java 下载文件的基础 URL（内网可达地址）
            @NotBlank String internalBaseUrl
    ) {
    }

    public record Storage(
            boolean listCacheEnabled,
            boolean linkCacheEnabled,
            List<String> localRootWhitelist
    ) {
    }

    public record Upload(
            DataSize maxSize,
            DataSize taskThreshold
    ) {
    }

    public record Bootstrap(
            String adminUsername,
            String adminPassword,
            String guestUsername,
            String guestPassword,
            boolean guestEnabled
    ) {
    }

    public record Download(
            // 下载签名有效期秒数，0 表示永不过期
            long signTtlSeconds,
            // 是否对所有文件强制签名（默认 false，仅密码目录需签名）
            boolean signAll
    ) {
    }

    public record Share(
            // 分享访问令牌（shareToken）有效期秒数，密码校验后签发（M7）
            long tokenTtlSeconds
    ) {
    }

    public record DataSize(
            long bytes
    ) {
        public static DataSize ofMegabytes(long megabytes) {
            return new DataSize(megabytes * 1024L * 1024L);
        }
    }
}
