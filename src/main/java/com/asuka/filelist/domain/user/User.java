package com.asuka.filelist.domain.user;

import java.util.List;

/**
 * 用户领域模型，封装认证和权限判定所需的核心字段。
 */
public record User(
        Long id,
        String username,
        String passwordHash,
        String passwordSalt,
        long passwordTs,
        String basePath,
        boolean disabled,
        int permission,
        List<Role> roles
) {
}
