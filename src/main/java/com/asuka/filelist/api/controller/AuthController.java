package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.LoginRequest;
import com.asuka.filelist.api.response.LoginResponse;
import com.asuka.filelist.application.auth.AuthApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    /**
     * 用户登录。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authApplicationService.login(request));
    }

    /**
     * 登出接口，M2 阶段由客户端删除 token。
     */
    @GetMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }
}
