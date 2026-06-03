package com.asuka.filelist.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 管理员创建角色请求。
 */
public record AdminCreateRoleRequest(
        @NotBlank String name,
        String description,
        Boolean defaultRole,
        @Valid List<PermissionScopeRequest> permissionScopes
) {
}
