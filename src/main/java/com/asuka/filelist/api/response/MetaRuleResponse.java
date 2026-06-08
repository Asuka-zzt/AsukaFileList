package com.asuka.filelist.api.response;

/**
 * 目录 Meta 规则 API 响应（仅管理员可见，故包含明文密码以便编辑）。
 */
public record MetaRuleResponse(
        Long id,
        String path,
        String password,
        boolean pSub,
        boolean writeEnabled,
        boolean wSub,
        String hide,
        boolean hSub,
        String readme,
        String header
) {
}
