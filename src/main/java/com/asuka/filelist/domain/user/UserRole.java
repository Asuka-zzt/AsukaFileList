package com.asuka.filelist.domain.user;

/**
 * 用户与角色的关联关系。
 */
public record UserRole(
        Long userId,
        Long roleId
) {
}
