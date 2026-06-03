package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotNull;

/**
 * 存储 ID 请求。
 */
public record StorageIdRequest(
        @NotNull Long id
) {
}
