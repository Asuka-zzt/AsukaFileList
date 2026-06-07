package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 移动请求，将 srcDir 下的 names 移动到 dstDir（同存储内）。
 */
public record FsMoveRequest(
        @NotBlank String srcDir,
        @NotBlank String dstDir,
        @NotEmpty List<String> names
) {
}
