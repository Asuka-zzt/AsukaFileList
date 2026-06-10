package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.SetWebdavPasswordRequest;
import com.asuka.filelist.api.request.UpdateMeRequest;
import com.asuka.filelist.api.response.CurrentUserResponse;
import com.asuka.filelist.application.auth.AuthApplicationService;
import com.asuka.filelist.application.auth.CurrentUserService;
import com.asuka.filelist.application.webdav.WebdavCredentialService;
import com.asuka.filelist.common.result.ApiResponse;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户接口。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CurrentUserService currentUserService;
    private final AuthApplicationService authApplicationService;
    private final WebdavCredentialService webdavCredentialService;

    public MeController(CurrentUserService currentUserService, AuthApplicationService authApplicationService,
                        WebdavCredentialService webdavCredentialService) {
        this.currentUserService = currentUserService;
        this.authApplicationService = authApplicationService;
        this.webdavCredentialService = webdavCredentialService;
    }

    /**
     * 查询当前用户。
     */
    @GetMapping
    public ApiResponse<CurrentUserResponse> current(CurrentUser currentUser) {
        return ApiResponse.success(currentUserService.toResponse(currentUser));
    }

    /**
     * 更新当前用户资料。
     */
    @PostMapping("/update")
    public ApiResponse<Void> update(CurrentUser currentUser, @Valid @RequestBody UpdateMeRequest request) {
        authApplicationService.updatePassword(currentUser, request);
        return ApiResponse.ok();
    }

    /**
     * 设置当前用户的 WebDAV 专用密码（仅存 HA1）。
     */
    @PostMapping("/webdav-password")
    public ApiResponse<Void> setWebdavPassword(CurrentUser currentUser, @Valid @RequestBody SetWebdavPasswordRequest request) {
        webdavCredentialService.setPassword(currentUser, request.password());
        return ApiResponse.ok();
    }

    /**
     * 清除当前用户的 WebDAV 密码（禁用 WebDAV 登录）。
     */
    @PostMapping("/webdav-password/clear")
    public ApiResponse<Void> clearWebdavPassword(CurrentUser currentUser) {
        webdavCredentialService.clearPassword(currentUser);
        return ApiResponse.ok();
    }
}
