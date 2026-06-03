package com.asuka.filelist.application.storage;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 将用户请求路径解析为存储运行时和驱动内 actualPath。
 */
@Service
public class StorageResolver {

    private final MountedStorageRegistry mountedStorageRegistry;

    public StorageResolver(MountedStorageRegistry mountedStorageRegistry) {
        this.mountedStorageRegistry = mountedStorageRegistry;
    }

    /**
     * 解析路径，未命中挂载时抛业务异常。
     */
    public ResolvedStoragePath resolve(String rawPath) {
        return tryResolve(rawPath)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORAGE_NOT_FOUND, "Storage not found"));
    }

    /**
     * 尝试解析路径，未命中时返回空。
     */
    public Optional<ResolvedStoragePath> tryResolve(String rawPath) {
        String path = PathUtils.fixAndCleanPath(rawPath);
        return mountedStorageRegistry.matchLongestPrefix(path)
                .map(runtime -> new ResolvedStoragePath(runtime, path, actualPath(path, runtime.storage().mountPath())));
    }

    /**
     * 去掉挂载前缀得到驱动内路径。
     */
    private String actualPath(String requestPath, String mountPath) {
        String mount = PathUtils.fixAndCleanPath(mountPath);
        if (PathUtils.pathEquals(requestPath, mount)) {
            return "/";
        }
        if ("/".equals(mount)) {
            return requestPath;
        }
        return PathUtils.fixAndCleanPath(requestPath.substring(mount.length()));
    }
}
