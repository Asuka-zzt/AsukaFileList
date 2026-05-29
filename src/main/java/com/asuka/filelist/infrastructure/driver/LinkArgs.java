package com.asuka.filelist.infrastructure.driver;

import java.util.Map;

public record LinkArgs(
        String ip,
        Map<String, String> headers,
        String type,
        boolean redirect
) {
}
