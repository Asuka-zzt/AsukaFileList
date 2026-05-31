package com.asuka.filelist.api.response;

import java.util.List;

/**
 * 当前登录用户响应对象。
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String basePath,
        int permission,
        boolean admin,
        List<RoleResponse> roles
) {
}
