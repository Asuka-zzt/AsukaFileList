package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 删除请求，删除 dir 下的 names 文件或目录。
 */
public record FsRemoveRequest(
        @NotBlank String dir,
        @NotEmpty List<String> names
) {
}
