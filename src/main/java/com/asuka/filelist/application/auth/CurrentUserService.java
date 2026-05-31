package com.asuka.filelist.application.auth;

import com.asuka.filelist.api.response.CurrentUserResponse;
import com.asuka.filelist.api.response.RoleResponse;
import com.asuka.filelist.application.user.RoleApplicationService;
import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.domain.user.User;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 当前用户加载服务，负责将 token 载荷转换为请求上下文。
 */
@Service
public class CurrentUserService {

    private final TokenService tokenService;
    private final UserApplicationService userApplicationService;
    private final RoleApplicationService roleApplicationService;

    public CurrentUserService(
            TokenService tokenService,
            UserApplicationService userApplicationService,
            RoleApplicationService roleApplicationService
    ) {
        this.tokenService = tokenService;
        this.userApplicationService = userApplicationService;
        this.roleApplicationService = roleApplicationService;
    }

    /**
     * 从访问 token 加载当前用户。
     */
    @Transactional(readOnly = true)
    public CurrentUser loadFromToken(String token) {
        TokenPayload payload = tokenService.parse(token);
        User user = userApplicationService.requireUser(payload.userId());
        if (user.disabled()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User is disabled");
        }
        if (user.passwordTs() != payload.passwordTs()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token has expired after password change");
        }
        return toCurrentUser(user);
    }

    /**
     * 将用户领域对象转换为请求上下文。
     */
    public CurrentUser toCurrentUser(User user) {
        return new CurrentUser(
                user.id(),
                user.username(),
                user.basePath(),
                user.permission(),
                roleApplicationService.isAdmin(user.roles()),
                user.roles()
        );
    }

    /**
     * 将当前用户上下文转换为响应对象。
     */
    public CurrentUserResponse toResponse(CurrentUser currentUser) {
        List<RoleResponse> roles = currentUser.roles().stream().map(roleApplicationService::toResponse).toList();
        return new CurrentUserResponse(
                currentUser.id(),
                currentUser.username(),
                currentUser.basePath(),
                currentUser.permission(),
                currentUser.admin(),
                roles
        );
    }
}
