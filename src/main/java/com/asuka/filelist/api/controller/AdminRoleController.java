package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.AdminCreateRoleRequest;
import com.asuka.filelist.api.response.RoleResponse;
import com.asuka.filelist.application.user.RoleApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员角色管理接口。
 */
@RestController
@RequestMapping("/api/admin/role")
public class AdminRoleController {

    private final RoleApplicationService roleApplicationService;

    public AdminRoleController(RoleApplicationService roleApplicationService) {
        this.roleApplicationService = roleApplicationService;
    }

    /**
     * 查询角色列表。
     */
    @GetMapping("/list")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.success(roleApplicationService.listRoleResponses());
    }

    /**
     * 创建角色。
     */
    @PostMapping("/create")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody AdminCreateRoleRequest request) {
        return ApiResponse.success(roleApplicationService.createRole(request));
    }
}
