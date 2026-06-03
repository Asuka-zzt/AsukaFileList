package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.UpdateMeRequest;
import com.asuka.filelist.api.response.CurrentUserResponse;
import com.asuka.filelist.application.auth.AuthApplicationService;
import com.asuka.filelist.application.auth.CurrentUserService;
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

    public MeController(CurrentUserService currentUserService, AuthApplicationService authApplicationService) {
        this.currentUserService = currentUserService;
        this.authApplicationService = authApplicationService;
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
}
