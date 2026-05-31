package com.asuka.filelist.api.response;

import com.asuka.filelist.domain.user.PermissionScope;

import java.util.List;

/**
 * 角色响应对象。
 */
public record RoleResponse(
        Long id,
        String name,
        String description,
        boolean defaultRole,
        List<PermissionScope> permissionScopes
) {
}
