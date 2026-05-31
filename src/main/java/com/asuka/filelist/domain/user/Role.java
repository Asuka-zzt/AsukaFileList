package com.asuka.filelist.domain.user;

import java.util.List;

/**
 * 角色聚合，包含路径权限范围。
 */
public record Role(
        Long id,
        String name,
        String description,
        List<PermissionScope> permissionScopes,
        boolean defaultRole
) {
}
