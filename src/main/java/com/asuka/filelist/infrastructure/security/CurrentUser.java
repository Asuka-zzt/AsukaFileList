package com.asuka.filelist.infrastructure.security;

import com.asuka.filelist.domain.user.Role;

import java.util.List;

/**
 * 已通过 token 校验的当前用户上下文。
 */
public record CurrentUser(
        Long id,
        String username,
        String basePath,
        int permission,
        boolean admin,
        List<Role> roles
) {
}
