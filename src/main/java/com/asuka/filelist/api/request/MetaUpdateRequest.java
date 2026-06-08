package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 更新目录 Meta 规则请求。
 */
public record MetaUpdateRequest(
        @NotNull Long id,
        @NotBlank String path,
        String password,
        Boolean pSub,
        Boolean writeEnabled,
        Boolean wSub,
        String hide,
        Boolean hSub,
        String readme,
        String header
) {
}
