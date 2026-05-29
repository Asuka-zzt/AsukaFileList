package com.asuka.filelist.infrastructure.driver;

public record ListArgs(
        String requestPath,
        boolean refresh
) {
}
