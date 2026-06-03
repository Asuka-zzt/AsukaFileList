package com.asuka.filelist.api.request;

import com.asuka.filelist.domain.user.PermissionBits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员创建用户请求。
 */
public record AdminCreateUserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        String basePath,
        @Min(0) @Max(PermissionBits.ALL) Integer permission,
        Boolean disabled,
        List<Long> roleIds
) {
}
