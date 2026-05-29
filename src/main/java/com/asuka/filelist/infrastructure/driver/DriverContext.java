package com.asuka.filelist.infrastructure.driver;

import java.util.Map;

public record DriverContext(
        String requestPath,
        Map<String, Object> attributes
) {
}
