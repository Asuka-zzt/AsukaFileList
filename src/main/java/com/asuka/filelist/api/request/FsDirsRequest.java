package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 子目录列表请求（用于移动/复制目标选择）。
 */
public record FsDirsRequest(
        @NotBlank String path,
        String password
) {
}
