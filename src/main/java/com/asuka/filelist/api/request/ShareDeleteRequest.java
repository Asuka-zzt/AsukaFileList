package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotNull;

/**
 * 删除分享请求。
 */
public record ShareDeleteRequest(
        @NotNull Long id
) {
}
