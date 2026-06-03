package com.asuka.filelist.api.request;

import com.asuka.filelist.domain.user.PermissionBits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 角色路径权限范围请求。
 */
public record PermissionScopeRequest(
        @NotBlank String path,
        @Min(0) @Max(PermissionBits.ALL) Integer permission
) {
}
