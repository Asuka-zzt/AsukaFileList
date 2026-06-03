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
        Bootstrap bootstrap
) {

    public record Jwt(
            @NotBlank String secret,
            @Positive long tokenExpiresHours
    ) {
    }

    public record Ai(
            @NotBlank String baseUrl,
            @NotBlank String apiKey,
            @NotBlank String internalDownloadToken
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

    public record DataSize(
            long bytes
    ) {
        public static DataSize ofMegabytes(long megabytes) {
            return new DataSize(megabytes * 1024L * 1024L);
        }
    }
}
