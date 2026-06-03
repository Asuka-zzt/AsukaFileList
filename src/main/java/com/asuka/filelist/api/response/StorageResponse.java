package com.asuka.filelist.api.response;

import java.util.Map;

/**
 * 存储挂载响应。
 */
public record StorageResponse(
        Long id,
        String mountPath,
        Integer orderNo,
        String driver,
        Integer cacheExpiration,
        String status,
        Map<String, Object> addition,
        String remark,
        boolean disabled,
        boolean disableIndex,
        boolean enableSign,
        String orderBy,
        String orderDirection,
        String extractFolder,
        boolean webProxy,
        String webdavPolicy,
        boolean proxyRange
) {
}
