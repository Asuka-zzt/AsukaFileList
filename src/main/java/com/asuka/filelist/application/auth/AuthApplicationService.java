package com.asuka.filelist.application.auth;

import com.asuka.filelist.api.request.LoginRequest;
import com.asuka.filelist.api.request.UpdateMeRequest;
import com.asuka.filelist.api.response.LoginResponse;
import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.domain.user.User;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证用例服务，负责登录和当前用户密码更新。
 */
@Service
public class AuthApplicationService {

    private final UserApplicationService userApplicationService;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final CurrentUserService currentUserService;

    public AuthApplicationService(
            UserApplicationService userApplicationService,
            PasswordService passwordService,
            TokenService tokenService,
            CurrentUserService currentUserService
    ) {
        this.userApplicationService = userApplicationService;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.currentUserService = currentUserService;
    }

    /**
     * 用户登录并签发访问 token。
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userApplicationService.findByUsername(request.username());
        if (!canLogin(user, request.password())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
        }
        TokenIssue issue = tokenService.issue(user.id(), user.username(), user.passwordTs());
        CurrentUser currentUser = currentUserService.toCurrentUser(user);
        return new LoginResponse("Bearer", issue.token(), issue.expiresAt(), currentUserService.toResponse(currentUser));
    }

    /**
     * 当前用户修改密码。
     */
    @Transactional
    public void updatePassword(CurrentUser currentUser, UpdateMeRequest request) {
        User user = userApplicationService.requireUser(currentUser.id());
        if (!passwordService.matches(request.oldPassword(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Old password is incorrect");
        }
        userApplicationService.updatePassword(currentUser.id(), request.newPassword());
    }

    /**
     * 统一登录校验，避免泄露用户存在性。
     */
    private boolean canLogin(User user, String rawPassword) {
        return user != null
                && !user.disabled()
                && passwordService.matches(rawPassword, user.passwordHash());
    }
}
