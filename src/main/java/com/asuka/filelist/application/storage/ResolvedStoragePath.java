package com.asuka.filelist.application.storage;

/**
 * 用户请求路径解析到具体存储后的结果。
 */
public record ResolvedStoragePath(
        MountedStorageRuntime runtime,
        String requestPath,
        String actualPath
) {
}
