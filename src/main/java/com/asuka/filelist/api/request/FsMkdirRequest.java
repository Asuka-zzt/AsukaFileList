package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建目录请求，path 为待创建目录的完整路径。
 */
public record FsMkdirRequest(
        @NotBlank String path
) {
}
