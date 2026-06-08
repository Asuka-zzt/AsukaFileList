package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建目录 Meta 规则请求。布尔标志为空时按 false 处理。
 */
public record MetaCreateRequest(
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
