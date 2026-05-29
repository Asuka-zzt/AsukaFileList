package com.asuka.filelist.domain.storage;

import java.time.Instant;

public record Storage(
        Long id,
        String mountPath,
        Integer orderNo,
        String driver,
        Integer cacheExpiration,
        String status,
        String addition,
        String remark,
        Instant modifiedAt,
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
