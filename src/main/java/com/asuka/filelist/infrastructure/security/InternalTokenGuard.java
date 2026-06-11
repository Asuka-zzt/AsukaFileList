package com.asuka.filelist.infrastructure.security;

import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 内部接口鉴权：校验请求头 {@code Authorization: Bearer <master_token>}。
 * 供 AI 服务回连的内部端点（下载 / 状态回调）使用。
 */
@Component
public class InternalTokenGuard {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String masterToken;

    public InternalTokenGuard(AsukaProperties properties) {
        this.masterToken = properties.ai().internalDownloadToken();
    }

    /** 校验 master token，不匹配抛 UNAUTHORIZED。 */
    public void verify(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)
                || !authorization.substring(BEARER_PREFIX.length()).trim().equals(masterToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid internal token");
        }
    }
}
