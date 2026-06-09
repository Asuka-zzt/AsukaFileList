package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新指定路径子树索引请求。
 */
public record IndexUpdateRequest(
        @NotBlank String path
) {
}
