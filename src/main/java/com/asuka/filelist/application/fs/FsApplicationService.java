package com.asuka.filelist.application.fs;

import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.application.storage.MountedStorageRegistry;
import com.asuka.filelist.application.storage.MountedStorageRuntime;
import com.asuka.filelist.application.storage.ResolvedStoragePath;
import com.asuka.filelist.application.storage.StorageResolver;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.fs.BasicFileObject;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.DriverGetter;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文件系统读服务，M3 接入存储挂载和只读驱动列表能力。
 */
@Service
public class FsApplicationService {

    private final StorageResolver storageResolver;
    private final MountedStorageRegistry mountedStorageRegistry;

    public FsApplicationService(StorageResolver storageResolver, MountedStorageRegistry mountedStorageRegistry) {
        this.storageResolver = storageResolver;
        this.mountedStorageRegistry = mountedStorageRegistry;
    }

    /**
     * 列出用户可见路径下的文件或虚拟挂载入口。
     */
    public FsListResponse list(CurrentUser currentUser, FsListRequest request) {
        String normalizedPath = PathUtils.joinBasePath(currentUser.basePath(), request.path());
        int page = request.effectivePage();
        int perPage = request.effectivePerPage();
        Optional<ResolvedStoragePath> resolved = storageResolver.tryResolve(normalizedPath);
        if (resolved.isPresent()) {
            return listResolvedPath(resolved.get(), page, perPage, request.refresh());
        }
        return listVirtualPath(normalizedPath, page, perPage);
    }

    /**
     * 列出真实存储路径。
     */
    private FsListResponse listResolvedPath(ResolvedStoragePath resolved, int page, int perPage, boolean refresh) {
        MountedStorageRuntime runtime = resolved.runtime();
        FileObject dir = getObject(runtime, resolved.actualPath());
        List<FileObjectResponse> objects = runtime.driver()
                .list(new DriverContext(resolved.requestPath(), Map.of()), dir, new ListArgs(resolved.requestPath(), refresh))
                .stream()
                .sorted(fileComparator(runtime.storage()))
                .map(object -> toResponse(runtime.storage(), object))
                .toList();
        return responsePage(objects, page, perPage, runtime.storage().driver());
    }

    /**
     * 列出虚拟挂载路径。
     */
    private FsListResponse listVirtualPath(String requestPath, int page, int perPage) {
        List<FileObjectResponse> entries = virtualChildren(requestPath);
        if (entries.isEmpty() && !PathUtils.pathEquals(requestPath, "/")) {
            throw new BusinessException(ErrorCode.STORAGE_NOT_FOUND, "Storage not found");
        }
        return responsePage(entries, page, perPage, "virtual");
    }

    /**
     * 获取目录对象，驱动必须支持 DriverGetter。
     */
    private FileObject getObject(MountedStorageRuntime runtime, String actualPath) {
        if (runtime.driver() instanceof DriverGetter getter) {
            return getter.get(new DriverContext(runtime.storage().mountPath(), Map.of()), actualPath);
        }
        return new BasicFileObject(
                actualPath,
                actualPath,
                "/".equals(actualPath) ? "/" : actualPath.substring(actualPath.lastIndexOf('/') + 1),
                0L,
                Instant.EPOCH,
                Instant.EPOCH,
                true,
                Map.of()
        );
    }

    /**
     * 生成当前虚拟路径下的一级挂载入口。
     */
    private List<FileObjectResponse> virtualChildren(String requestPath) {
        Map<String, FileObjectResponse> entries = new LinkedHashMap<>();
        for (MountedStorageRuntime runtime : mountedStorageRegistry.listMounts()) {
            immediateChild(requestPath, runtime.storage().mountPath()).ifPresent(child -> entries.putIfAbsent(child.path(), child));
        }
        return new ArrayList<>(entries.values());
    }

    /**
     * 计算某个 mountPath 在当前虚拟路径下的直接子入口。
     */
    private Optional<FileObjectResponse> immediateChild(String requestPath, String mountPath) {
        String current = PathUtils.fixAndCleanPath(requestPath);
        String mount = PathUtils.fixAndCleanPath(mountPath);
        if (PathUtils.pathEquals(current, mount) || !PathUtils.isSubPath(current, mount)) {
            return Optional.empty();
        }
        String remainder = "/".equals(current) ? mount.substring(1) : mount.substring(current.length() + 1);
        String name = remainder.split("/", 2)[0];
        String childPath = "/".equals(current) ? "/" + name : current + "/" + name;
        return Optional.of(virtualEntry(childPath, name));
    }

    /**
     * 创建虚拟目录响应对象。
     */
    private FileObjectResponse virtualEntry(String path, String name) {
        return new FileObjectResponse("virtual:" + path, path, name, 0L, true, Instant.EPOCH, Instant.EPOCH,
                "", "", 1, Map.of(), "virtual");
    }

    /**
     * 将驱动内对象映射回用户可见路径响应。
     */
    private FileObjectResponse toResponse(Storage storage, FileObject object) {
        String path = toRequestPath(storage.mountPath(), object.path());
        return new FileObjectResponse(object.id(), path, object.name(), object.size(), object.directory(),
                object.modifiedAt(), object.createdAt(), "", "", object.directory() ? 1 : 0,
                object.hashInfo(), storage.driver());
    }

    /**
     * 将 actualPath 拼回挂载路径。
     */
    private String toRequestPath(String mountPath, String actualPath) {
        if ("/".equals(actualPath)) {
            return PathUtils.fixAndCleanPath(mountPath);
        }
        if ("/".equals(PathUtils.fixAndCleanPath(mountPath))) {
            return PathUtils.fixAndCleanPath(actualPath);
        }
        return PathUtils.fixAndCleanPath(mountPath + "/" + actualPath.substring(1));
    }

    /**
     * 按 storage 排序配置构造文件对象比较器。
     */
    private Comparator<FileObject> fileComparator(Storage storage) {
        Comparator<FileObject> comparator = Comparator.comparing(FileObject::name, String.CASE_INSENSITIVE_ORDER);
        if ("size".equals(storage.orderBy())) {
            comparator = Comparator.comparingLong(FileObject::size);
        }
        if ("modified".equals(storage.orderBy())) {
            comparator = Comparator.comparing(FileObject::modifiedAt);
        }
        if ("desc".equals(storage.orderDirection())) {
            comparator = comparator.reversed();
        }
        if ("front".equals(storage.extractFolder())) {
            comparator = Comparator.comparing(FileObject::directory).reversed().thenComparing(comparator);
        }
        return comparator;
    }

    /**
     * 响应分页。
     */
    private FsListResponse responsePage(List<FileObjectResponse> all, int page, int perPage, String provider) {
        if (perPage == -1) {
            return new FsListResponse(all, all.size(), page, perPage, false, "", "", false, provider);
        }
        int from = Math.min((page - 1) * perPage, all.size());
        int to = Math.min(from + perPage, all.size());
        boolean hasMore = to < all.size();
        return new FsListResponse(all.subList(from, to), all.size(), page, perPage, hasMore, "", "", false, provider);
    }
}
