package com.asuka.filelist.infrastructure.security;

import com.asuka.filelist.application.auth.CurrentUserService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bearer token 认证拦截器。
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = "asuka.currentUser";

    private static final String BEARER_PREFIX = "Bearer ";

    private final CurrentUserService currentUserService;

    public AuthenticationInterceptor(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    /**
     * 在 Controller 执行前完成 token 校验和管理员路径校验。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (shouldSkip(request)) {
            return true;
        }
        CurrentUser currentUser = currentUserService.loadFromToken(extractToken(request));
        if (isAdminPath(request) && !currentUser.admin()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Admin permission required");
        }
        request.setAttribute(CURRENT_USER_ATTRIBUTE, currentUser);
        return true;
    }

    /**
     * 判断无需认证的路径。
     */
    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/api/auth/login".equals(path)
                || "/api/health".equals(path)
                || path.startsWith("/api/public/share/")
                || path.startsWith("/sd/")
                || path.startsWith("/actuator")
                || "/error".equals(path);
    }

    /**
     * 判断是否为管理员接口。
     */
    private boolean isAdminPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/admin/");
    }

    /**
     * 从 Authorization 头读取 Bearer token。
     */
    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing bearer token");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing bearer token");
        }
        return token;
    }
}
