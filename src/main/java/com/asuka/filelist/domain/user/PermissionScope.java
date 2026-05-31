package com.asuka.filelist.domain.user;

/**
 * 角色在指定路径下授予的权限范围。
 */
public record PermissionScope(
        String path,
        int permission
) {
}
