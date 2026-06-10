package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 设置当前用户 WebDAV 专用密码请求。挂载时配合用户名经 Digest 使用。
 */
public record SetWebdavPasswordRequest(
        @NotBlank @Size(min = 6, max = 128) String password
) {
}
