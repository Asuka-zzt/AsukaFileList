package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 文件/目录详情请求。
 */
public record FsGetRequest(
        @NotBlank String path,
        String password
) {
}
