package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 重命名请求，path 为现有对象路径，name 为新名称。
 */
public record FsRenameRequest(
        @NotBlank String path,
        @NotBlank String name
) {
}
