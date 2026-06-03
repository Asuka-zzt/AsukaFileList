package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 当前用户资料更新请求，M2 仅支持修改密码。
 */
public record UpdateMeRequest(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {
}
