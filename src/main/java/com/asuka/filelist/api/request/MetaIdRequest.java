package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotNull;

/**
 * 目录 Meta 规则 ID 请求。
 */
public record MetaIdRequest(
        @NotNull Long id
) {
}
