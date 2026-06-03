package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.AdminCreateUserRequest;
import com.asuka.filelist.api.response.UserResponse;
import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员用户管理接口。
 */
@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    private final UserApplicationService userApplicationService;

    public AdminUserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    /**
     * 查询用户列表。
     */
    @GetMapping("/list")
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.success(userApplicationService.listUserResponses());
    }

    /**
     * 创建用户。
     */
    @PostMapping("/create")
    public ApiResponse<UserResponse> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ApiResponse.success(userApplicationService.createUser(request));
    }
}
