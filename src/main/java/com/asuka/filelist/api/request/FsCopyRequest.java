package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 复制请求，将 srcDir 下的 names 复制到 dstDir（同存储内）。
 */
public record FsCopyRequest(
        @NotBlank String srcDir,
        @NotBlank String dstDir,
        @NotEmpty List<String> names
) {
}
