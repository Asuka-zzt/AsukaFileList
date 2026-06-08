package com.asuka.filelist.application.fs;

import com.asuka.filelist.api.request.FsCopyRequest;
import com.asuka.filelist.api.request.FsDirsRequest;
import com.asuka.filelist.api.request.FsGetRequest;
import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.request.FsMkdirRequest;
import com.asuka.filelist.api.request.FsMoveRequest;
import com.asuka.filelist.api.request.FsRemoveRequest;
import com.asuka.filelist.api.request.FsRenameRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.application.storage.MountedStorageRegistry;
import com.asuka.filelist.application.storage.MountedStorageRuntime;
import com.asuka.filelist.application.storage.ResolvedStoragePath;
import com.asuka.filelist.application.meta.MetaApplicationService;
import com.asuka.filelist.application.storage.StorageResolver;
import com.asuka.filelist.application.user.PermissionApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.fs.BasicFileObject;
import com.asuka.filelist.domain.fs.FileLink;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.domain.meta.ResolvedMeta;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.domain.user.PermissionBits;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.DriverGetter;
import com.asuka.filelist.infrastructure.driver.DriverWriter;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import com.asuka.filelist.infrastructure.driver.UploadFile;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 文件系统应用服务。M3 接入存储挂载与只读列表，M4 补全 get/dirs 与增删改/上传/下载读写闭环。
 */
@Service
public class FsApplicationService {

    private final StorageResolver storageResolver;
    private final MountedStorageRegistry mountedStorageRegistry;
    private final PermissionApplicationService permissionApplicationService;
    private final MetaApplicationService metaApplicationService;

    public FsApplicationService(StorageResolver storageResolver, MountedStorageRegistry mountedStorageRegistry,
                                PermissionApplicationService permissionApplicationService,
                                MetaApplicationService metaApplicationService) {
        this.storageResolver = storageResolver;
        this.mountedStorageRegistry = mountedStorageRegistry;
        this.permissionApplicationService = permissionApplicationService;
        this.metaApplicationService = metaApplicationService;
    }

    /**
     * 列出用户可见路径下的文件或虚拟挂载入口。
     * M3: 支持挂载点虚拟列表与 LocalDriver 真实列表；修复 review 中的路径权限与 basePath 问题。
     */
    public FsListResponse list(CurrentUser currentUser, FsListRequest request) {
        String visiblePath = PathUtils.fixAndCleanPath(request.path());
        String normalizedPath = PathUtils.joinBasePath(currentUser.basePath(), request.path());
        int page = request.effectivePage();
        int perPage = request.effectivePerPage();

        // P1 fix (review): 路径限制用户在解析 storage 前先检查 effective perm，PATH_LIMIT 未命中 scope 时 resolve=0
        // 同时对 eff=0 的情况统一拒绝，防止无权限用户列目录
        if (permissionApplicationService.resolvePermission(currentUser, visiblePath) == 0) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Permission denied");
        }

        // M5: 解析就近 Meta，做目录密码校验，并计算隐藏可见性与写开关
        ResolvedMeta meta = metaApplicationService.resolve(normalizedPath);
        requirePassword(currentUser, visiblePath, meta, request.password());
        boolean canViewHidden = permissionApplicationService.hasPermission(currentUser, visiblePath, PermissionBits.VIEW_HIDDEN);
        boolean write = permissionApplicationService.hasPermission(currentUser, visiblePath, PermissionBits.WRITE_UPLOAD)
                || meta.writeEnabled();

        Optional<ResolvedStoragePath> resolved = storageResolver.tryResolve(normalizedPath);
        if (resolved.isPresent()) {
            return listResolvedPath(resolved.get(), page, perPage, request.refresh(), currentUser, meta, canViewHidden, write);
        }
        return listVirtualPath(visiblePath, page, perPage, currentUser, meta, canViewHidden, write);
    }

    /**
     * M5: 目录密码校验。无 BYPASS_PASSWORD 权限时，必须提供与 Meta 一致的密码。
     */
    private void requirePassword(CurrentUser currentUser, String visiblePath, ResolvedMeta meta, String provided) {
        if (!meta.hasPassword()
                || permissionApplicationService.hasPermission(currentUser, visiblePath, PermissionBits.BYPASS_PASSWORD)) {
            return;
        }
        if (provided == null || provided.isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_REQUIRED, "Directory password is required");
        }
        if (!meta.password().equals(provided)) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT, "Directory password is incorrect");
        }
    }

    /**
     * M5: 隐藏过滤。无 VIEW_HIDDEN 权限时，剔除命中 hide 正则的条目。
     */
    private List<FileObjectResponse> applyHide(List<FileObjectResponse> objects, ResolvedMeta meta, boolean canViewHidden) {
        List<String> regexes = meta.hideRegexes();
        if (canViewHidden || regexes.isEmpty()) {
            return objects;
        }
        List<Pattern> patterns = regexes.stream().map(Pattern::compile).toList();
        return objects.stream()
                .filter(obj -> patterns.stream().noneMatch(pattern -> pattern.matcher(obj.name()).find()))
                .toList();
    }

    /**
     * M4: 获取单个文件或目录详情。
     */
    public FileObjectResponse get(CurrentUser currentUser, FsGetRequest request) {
        requireReadable(currentUser, PathUtils.fixAndCleanPath(request.path()));
        ResolvedStoragePath resolved = storageResolver.resolve(internalPath(currentUser, request.path()));
        FileObject object = getObject(resolved.runtime(), resolved.actualPath());
        return toResponse(resolved.runtime().storage(), object, currentUser.basePath());
    }

    /**
     * M4: 仅返回目录列表，复用 list 的权限与解析逻辑。
     */
    public List<FileObjectResponse> dirs(CurrentUser currentUser, FsDirsRequest request) {
        FsListResponse listed = list(currentUser, new FsListRequest(request.path(), request.password(), false, 1, -1));
        return listed.content().stream().filter(FileObjectResponse::isDir).toList();
    }

    /**
     * M4: 新建目录，path 为待创建目录完整路径。
     */
    public FileObjectResponse mkdir(CurrentUser currentUser, FsMkdirRequest request) {
        requirePermission(currentUser, PathUtils.fixAndCleanPath(request.path()), PermissionBits.WRITE_UPLOAD);
        ResolvedStoragePath resolved = storageResolver.resolve(internalPath(currentUser, request.path()));
        String actualPath = resolved.actualPath();
        if ("/".equals(actualPath)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot create mount root directory");
        }
        FileObject created = writerOf(resolved.runtime())
                .mkdir(contextOf(resolved), parentOf(actualPath), lastSegment(actualPath));
        return toResponse(resolved.runtime().storage(), created, currentUser.basePath());
    }

    /**
     * M4: 重命名文件或目录。
     */
    public void rename(CurrentUser currentUser, FsRenameRequest request) {
        PathUtils.validateNameComponent(request.name());
        requirePermission(currentUser, PathUtils.fixAndCleanPath(request.path()), PermissionBits.RENAME);
        ResolvedStoragePath resolved = storageResolver.resolve(internalPath(currentUser, request.path()));
        writerOf(resolved.runtime()).rename(contextOf(resolved), resolved.actualPath(), request.name());
    }

    /**
     * M4: 将 srcDir 下的 names 移动到 dstDir（同存储）。
     */
    public void move(CurrentUser currentUser, FsMoveRequest request) {
        batchTransfer(currentUser, request.srcDir(), request.dstDir(), request.names(), PermissionBits.MOVE, true);
    }

    /**
     * M4: 将 srcDir 下的 names 复制到 dstDir（同存储）。
     */
    public void copy(CurrentUser currentUser, FsCopyRequest request) {
        batchTransfer(currentUser, request.srcDir(), request.dstDir(), request.names(), PermissionBits.COPY, false);
    }

    /**
     * M4: 删除 dir 下的 names 文件或目录。
     */
    public void remove(CurrentUser currentUser, FsRemoveRequest request) {
        requirePermission(currentUser, PathUtils.fixAndCleanPath(request.dir()), PermissionBits.REMOVE);
        ResolvedStoragePath resolved = storageResolver.resolve(internalPath(currentUser, request.dir()));
        DriverWriter writer = writerOf(resolved.runtime());
        DriverContext context = contextOf(resolved);
        for (String name : request.names()) {
            PathUtils.validateNameComponent(name);
            writer.remove(context, joinActual(resolved.actualPath(), name));
        }
    }

    /**
     * M4: 流式上传到 rawDirPath 目录。
     */
    public FileObjectResponse put(CurrentUser currentUser, String rawDirPath, UploadFile file) {
        PathUtils.validateNameComponent(file.name());
        requirePermission(currentUser, PathUtils.fixAndCleanPath(rawDirPath), PermissionBits.WRITE_UPLOAD);
        ResolvedStoragePath resolved = storageResolver.resolve(internalPath(currentUser, rawDirPath));
        FileObject written = writerOf(resolved.runtime()).put(contextOf(resolved), resolved.actualPath(), file);
        return toResponse(resolved.runtime().storage(), written, currentUser.basePath());
    }

    /**
     * M4: 解析下载目标，返回文件元数据与驱动链接。
     */
    public FsDownloadTarget link(CurrentUser currentUser, String rawPath, LinkArgs args) {
        requireReadable(currentUser, PathUtils.fixAndCleanPath(rawPath));
        ResolvedStoragePath resolved = storageResolver.resolve(internalPath(currentUser, rawPath));
        FileObject file = getObject(resolved.runtime(), resolved.actualPath());
        if (file.directory()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot download a directory");
        }
        FileLink fileLink = resolved.runtime().driver().link(contextOf(resolved), file, args);
        return new FsDownloadTarget(file, fileLink);
    }

    /**
     * 拼接用户 basePath 得到内部解析路径。
     */
    private String internalPath(CurrentUser currentUser, String rawPath) {
        return PathUtils.joinBasePath(currentUser.basePath(), rawPath);
    }

    /**
     * 读权限校验：有效权限为 0 时拒绝（与 list 一致）。
     */
    private void requireReadable(CurrentUser currentUser, String visiblePath) {
        if (permissionApplicationService.resolvePermission(currentUser, visiblePath) == 0) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Permission denied");
        }
    }

    /**
     * 写权限位校验。
     */
    private void requirePermission(CurrentUser currentUser, String visiblePath, int mask) {
        if (!permissionApplicationService.hasPermission(currentUser, visiblePath, mask)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Permission denied");
        }
    }

    /**
     * 取驱动写能力，不支持时抛 DRIVER_NOT_SUPPORTED。
     */
    private DriverWriter writerOf(MountedStorageRuntime runtime) {
        if (runtime.driver() instanceof DriverWriter writer) {
            return writer;
        }
        throw new BusinessException(ErrorCode.DRIVER_NOT_SUPPORTED, "Driver does not support write operations");
    }

    /**
     * 构造驱动调用上下文。
     */
    private DriverContext contextOf(ResolvedStoragePath resolved) {
        return new DriverContext(resolved.requestPath(), Map.of());
    }

    /**
     * 移动/复制公共逻辑，校验权限与同存储约束后逐项执行。
     */
    private void batchTransfer(CurrentUser currentUser, String srcDir, String dstDir, List<String> names, int mask, boolean move) {
        requirePermission(currentUser, PathUtils.fixAndCleanPath(srcDir), mask);
        requirePermission(currentUser, PathUtils.fixAndCleanPath(dstDir), mask);
        ResolvedStoragePath src = storageResolver.resolve(internalPath(currentUser, srcDir));
        ResolvedStoragePath dst = storageResolver.resolve(internalPath(currentUser, dstDir));
        if (!src.runtime().storage().id().equals(dst.runtime().storage().id())) {
            throw new BusinessException(ErrorCode.DRIVER_NOT_SUPPORTED, "Cross-storage transfer is not supported yet");
        }
        DriverWriter writer = writerOf(src.runtime());
        DriverContext context = contextOf(src);
        for (String name : names) {
            PathUtils.validateNameComponent(name);
            String srcPath = joinActual(src.actualPath(), name);
            if (move) {
                writer.move(context, srcPath, dst.actualPath());
            } else {
                writer.copy(context, srcPath, dst.actualPath());
            }
        }
    }

    /**
     * 拼接目录 actualPath 与子项名。
     */
    private String joinActual(String dir, String name) {
        return "/".equals(dir) ? "/" + name : dir + "/" + name;
    }

    /**
     * 取路径最后一段名称。
     */
    private String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 取父目录 actualPath，根的父仍为根。
     */
    private String parentOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx <= 0 ? "/" : path.substring(0, idx);
    }

    /**
     * 列出真实存储路径。
     */
    private FsListResponse listResolvedPath(ResolvedStoragePath resolved, int page, int perPage, boolean refresh,
                                            CurrentUser currentUser, ResolvedMeta meta, boolean canViewHidden, boolean write) {
        MountedStorageRuntime runtime = resolved.runtime();
        FileObject dir = getObject(runtime, resolved.actualPath());
        List<FileObjectResponse> objects = runtime.driver()
                .list(new DriverContext(resolved.requestPath(), Map.of()), dir, new ListArgs(resolved.requestPath(), refresh))
                .stream()
                .sorted(fileComparator(runtime.storage()))
                .map(object -> toResponse(runtime.storage(), object, currentUser.basePath()))
                .toList();
        objects = applyHide(objects, meta, canViewHidden);
        return responsePage(objects, page, perPage, runtime.storage().driver(), meta, write);
    }

    /**
     * 列出虚拟挂载路径。
     */
    private FsListResponse listVirtualPath(String visiblePath, int page, int perPage, CurrentUser currentUser,
                                           ResolvedMeta meta, boolean canViewHidden, boolean write) {
        List<FileObjectResponse> entries = virtualChildren(visiblePath, currentUser.basePath(), currentUser);
        if (entries.isEmpty() && !PathUtils.pathEquals(visiblePath, "/")) {
            throw new BusinessException(ErrorCode.STORAGE_NOT_FOUND, "Storage not found");
        }
        entries = applyHide(entries, meta, canViewHidden);
        return responsePage(entries, page, perPage, "virtual", meta, write);
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
     * 仅返回当前用户权限可见的挂载入口（P1 review fix for PATH_LIMIT 用户）。
     */
    private List<FileObjectResponse> virtualChildren(String visibleRequestPath, String basePath, CurrentUser currentUser) {
        Map<String, FileObjectResponse> entries = new LinkedHashMap<>();
        for (MountedStorageRuntime runtime : mountedStorageRegistry.listMounts()) {
            String visibleMount = toVisiblePath(runtime.storage().mountPath(), basePath);
            immediateChild(visibleRequestPath, visibleMount).ifPresent(child -> {
                if (permissionApplicationService.resolvePermission(currentUser, child.path()) != 0) {
                    entries.putIfAbsent(child.path(), child);
                }
            });
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
     * 使用 toVisiblePath 剥离 basePath，避免响应中泄露内部 base 前缀（P2 review fix）。
     */
    private FileObjectResponse toResponse(Storage storage, FileObject object, String basePath) {
        String visibleMount = toVisiblePath(storage.mountPath(), basePath);
        String path = toRequestPath(visibleMount, object.path());
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
    private FsListResponse responsePage(List<FileObjectResponse> all, int page, int perPage, String provider,
                                        ResolvedMeta meta, boolean write) {
        if (perPage == -1) {
            return new FsListResponse(all, all.size(), page, perPage, false, meta.readme(), meta.header(), write, provider);
        }
        // P3 fix (review): 使用 long 避免大 page * perPage 导致 int 溢出（负数），导致 subList 异常
        long offset = (page - 1L) * perPage;
        int from = (int) Math.min(offset, (long) all.size());
        if (from < 0) {
            from = 0;
        }
        int to = Math.min(from + perPage, all.size());
        boolean hasMore = to < all.size();
        return new FsListResponse(all.subList(from, to), all.size(), page, perPage, hasMore,
                meta.readme(), meta.header(), write, provider);
    }

    /**
     * 将内部存储路径（含用户 basePath）转换为用户可见的相对路径（P2 review fix）。
     * 例如 base=/home/alice, internal=/home/alice/local => /local
     */
    private String toVisiblePath(String internalPath, String basePath) {
        String b = PathUtils.fixAndCleanPath(basePath);
        String p = PathUtils.fixAndCleanPath(internalPath);
        if ("/".equals(b)) {
            return p;
        }
        if (PathUtils.pathEquals(b, p)) {
            return "/";
        }
        if (PathUtils.isSubPath(b, p)) {
            String rem = p.substring(b.length());
            return PathUtils.fixAndCleanPath(rem);
        }
        return p;
    }
}
