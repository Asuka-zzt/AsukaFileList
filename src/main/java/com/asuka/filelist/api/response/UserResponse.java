package com.asuka.filelist.api.response;

import java.util.List;

/**
 * 用户响应对象，不暴露密码和盐。
 */
public record UserResponse(
        Long id,
        String username,
        String basePath,
        boolean disabled,
        int permission,
        boolean admin,
        List<RoleResponse> roles
) {
}
