package com.asuka.filelist.infrastructure.driver.baidu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 百度网盘驱动私有配置（存于 storages.addition JSON）。
 *
 * @param refreshToken OAuth refresh_token（长期有效）
 * @param clientId     应用 AppKey
 * @param clientSecret 应用 SecretKey
 * @param rootPath     网盘内根路径；空=/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaiduDriverAddition(
        String refreshToken,
        String clientId,
        String clientSecret,
        String rootPath
) {

    /**
     * 规范化根路径：空回退 /，去尾斜杠。
     */
    public String normalizedRootPath() {
        if (rootPath == null || rootPath.isBlank() || "/".equals(rootPath.trim())) {
            return "/";
        }
        String trimmed = rootPath.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed.replaceAll("/+$", "");
    }
}
