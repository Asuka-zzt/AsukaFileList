package com.asuka.filelist.infrastructure.driver;

public record DriverConfig(
        String name,
        boolean localSort,
        boolean onlyLocal,
        boolean onlyProxy,
        boolean noCache,
        boolean noUpload,
        String defaultRoot,
        boolean checkStatus
) {
}
