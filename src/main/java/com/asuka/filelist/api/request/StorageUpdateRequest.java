package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 更新存储挂载请求。
 */
public record StorageUpdateRequest(
        @NotNull Long id,
        @NotBlank String mountPath,
        @NotBlank String driver,
        Map<String, Object> addition,
        Integer orderNo,
        String remark,
        Boolean disabled
) {
}
