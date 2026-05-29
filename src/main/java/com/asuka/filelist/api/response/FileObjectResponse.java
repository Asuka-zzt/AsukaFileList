package com.asuka.filelist.api.response;

import java.time.Instant;
import java.util.Map;

public record FileObjectResponse(
        String id,
        String path,
        String name,
        long size,
        boolean isDir,
        Instant modified,
        Instant created,
        String sign,
        String thumb,
        int type,
        Map<String, String> hashInfo,
        String storageClass
) {
}
