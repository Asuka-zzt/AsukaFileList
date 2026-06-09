package com.asuka.filelist.infrastructure.driver.s3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * S3 驱动私有配置（存于 storages.addition JSON）。
 *
 * @param bucket          桶名（必填）
 * @param endpoint        S3 兼容端点；空=AWS 官方
 * @param region          区域，空时回退 us-east-1
 * @param accessKeyId     Access Key（必填）
 * @param secretAccessKey Secret Key（必填）
 * @param rootFolder      桶内根前缀；空=桶根
 * @param pathStyle       路径风格寻址（MinIO 等常需）
 * @param signExpireSec   预签名有效期秒；<=0 时回退 900
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record S3DriverAddition(
        String bucket,
        String endpoint,
        String region,
        String accessKeyId,
        String secretAccessKey,
        String rootFolder,
        boolean pathStyle,
        int signExpireSec
) {

    /**
     * 区域，缺省回退 us-east-1。
     */
    public String effectiveRegion() {
        return region == null || region.isBlank() ? "us-east-1" : region.trim();
    }

    /**
     * 预签名有效期秒，非法值回退 900。
     */
    public int effectiveSignExpireSec() {
        return signExpireSec > 0 ? signExpireSec : 900;
    }

    /**
     * 规范化根前缀：去首尾斜杠，空根返回 ""。
     */
    public String normalizedRootFolder() {
        if (rootFolder == null || rootFolder.isBlank()) {
            return "";
        }
        return rootFolder.trim().replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
