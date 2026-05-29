package com.asuka.filelist.domain.fs;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record FileLink(
        URI url,
        Map<String, String> headers,
        Duration expiration,
        boolean ipCacheKey,
        Integer concurrency,
        Integer partSize
) {
}
