package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 创建存储挂载请求。
 */
public record StorageCreateRequest(
        @NotBlank String mountPath,
        @NotBlank String driver,
        Map<String, Object> addition,
        Integer orderNo,
        String remark,
        Boolean disabled
) {
}
