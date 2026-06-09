package com.asuka.filelist.application.search;

import com.asuka.filelist.api.response.SearchPageResponse;
import com.asuka.filelist.api.response.SearchResultResponse;
import com.asuka.filelist.application.storage.MountedStorageRegistry;
import com.asuka.filelist.application.storage.MountedStorageRuntime;
import com.asuka.filelist.application.task.TaskExecutor;
import com.asuka.filelist.application.task.TaskProgress;
import com.asuka.filelist.application.user.PermissionApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.domain.task.TaskType;
import com.asuka.filelist.infrastructure.persistence.entity.FileIndexNodeEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.FileIndexNodeMapper;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件名索引服务：重建索引（作为任务执行）与文件名搜索（带权限/basePath 过滤）。
 */
@Service
public class FileNameIndexService {

    private final MountedStorageRegistry registry;
    private final FileIndexNodeMapper nodeMapper;
    private final FileTreeWalker walker;
    private final PermissionApplicationService permissionApplicationService;
    private final TaskExecutor taskExecutor;

    public FileNameIndexService(MountedStorageRegistry registry, FileIndexNodeMapper nodeMapper,
                                FileTreeWalker walker, PermissionApplicationService permissionApplicationService,
                                TaskExecutor taskExecutor) {
        this.registry = registry;
        this.nodeMapper = nodeMapper;
        this.walker = walker;
        this.permissionApplicationService = permissionApplicationService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 提交索引重建任务，返回 taskId；storageId 为空表示全部存储。
     */
    public long submitBuild(CurrentUser user, Long storageId) {
        String payload = "{\"storageId\":" + (storageId == null ? "null" : storageId) + "}";
        return taskExecutor.submit(TaskType.BUILD_INDEX, user.id(), payload, progress -> build(storageId, progress));
    }

    /**
     * 重建索引：删除目标存储旧节点，遍历后批量写入。
     */
    public void build(Long storageId, TaskProgress progress) {
        List<MountedStorageRuntime> targets = registry.listMounts().stream()
                .filter(runtime -> storageId == null || runtime.storage().id().equals(storageId))
                .toList();
        if (storageId != null && targets.isEmpty()) {
            throw new BusinessException(ErrorCode.STORAGE_NOT_FOUND, "Storage not found or not mounted");
        }
        for (MountedStorageRuntime runtime : targets) {
            progress.checkCanceled();
            Long sid = runtime.storage().id();
            nodeMapper.delete(new LambdaQueryWrapper<FileIndexNodeEntity>().eq(FileIndexNodeEntity::getStorageId, sid));
            List<FileIndexNodeEntity> nodes = new ArrayList<>();
            walker.walk(runtime, object -> nodes.add(toNode(object, sid)), progress);
            insertAll(nodes, progress);
        }
        progress.report(100);
    }

    /**
     * 文件名搜索：DB 分页 + 可见路径映射 + 权限过滤。
     */
    public SearchPageResponse search(CurrentUser user, String keyword, int page, int perPage) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Keyword is required");
        }
        LambdaQueryWrapper<FileIndexNodeEntity> query = new LambdaQueryWrapper<FileIndexNodeEntity>()
                .like(FileIndexNodeEntity::getName, keyword.trim())
                .orderByAsc(FileIndexNodeEntity::getName);
        IPage<FileIndexNodeEntity> result = nodeMapper.selectPage(new Page<>(page, perPage), query);

        Map<Long, String> mountPaths = registry.listMounts().stream()
                .collect(Collectors.toMap(runtime -> runtime.storage().id(),
                        runtime -> runtime.storage().mountPath(), (a, b) -> a));

        List<SearchResultResponse> items = new ArrayList<>();
        for (FileIndexNodeEntity node : result.getRecords()) {
            String mount = mountPaths.get(node.getStorageId());
            if (mount == null) {
                continue; // 存储已卸载/禁用
            }
            String internal = joinMount(mount, actualPath(node.getParent(), node.getName()));
            // basePath 硬边界：用户根之外的条目不可见
            if (!PathUtils.isSubPath(user.basePath(), internal)) {
                continue;
            }
            String visible = toVisiblePath(internal, user.basePath());
            if (permissionApplicationService.resolvePermission(user, visible) == 0) {
                continue;
            }
            items.add(new SearchResultResponse(visible, node.getName(),
                    Boolean.TRUE.equals(node.getIsDir()), node.getSize() == null ? 0L : node.getSize()));
        }
        return new SearchPageResponse(items, result.getTotal(), page, perPage);
    }

    /**
     * 逐条插入并节流上报进度。
     */
    private void insertAll(List<FileIndexNodeEntity> nodes, TaskProgress progress) {
        int total = nodes.size();
        for (int i = 0; i < total; i++) {
            progress.checkCanceled();
            nodeMapper.insert(nodes.get(i));
            progress.report((i + 1) * 100 / Math.max(1, total));
        }
    }

    /**
     * FileObject 转索引节点。
     */
    private FileIndexNodeEntity toNode(FileObject object, Long storageId) {
        FileIndexNodeEntity entity = new FileIndexNodeEntity();
        entity.setParent(parentOf(object.path()));
        entity.setName(object.name());
        entity.setIsDir(object.directory());
        entity.setSize(object.size());
        entity.setStorageId(storageId);
        return entity;
    }

    /**
     * 取 actualPath 的父目录，根返回 "/"。
     */
    private String parentOf(String actualPath) {
        String clean = PathUtils.fixAndCleanPath(actualPath);
        int idx = clean.lastIndexOf('/');
        return idx <= 0 ? "/" : clean.substring(0, idx);
    }

    /**
     * 由 parent + name 还原 actualPath。
     */
    private String actualPath(String parent, String name) {
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    /**
     * 将挂载路径与 actualPath 拼为内部绝对路径。
     */
    private String joinMount(String mountPath, String actualPath) {
        if ("/".equals(PathUtils.fixAndCleanPath(mountPath))) {
            return PathUtils.fixAndCleanPath(actualPath);
        }
        return PathUtils.fixAndCleanPath(mountPath + actualPath);
    }

    /**
     * 剥离用户 basePath 得到可见路径（与 FsApplicationService 一致）。
     */
    private String toVisiblePath(String internalPath, String basePath) {
        String base = PathUtils.fixAndCleanPath(basePath);
        String path = PathUtils.fixAndCleanPath(internalPath);
        if ("/".equals(base)) {
            return path;
        }
        if (PathUtils.pathEquals(base, path)) {
            return "/";
        }
        if (PathUtils.isSubPath(base, path)) {
            return PathUtils.fixAndCleanPath(path.substring(base.length()));
        }
        return path;
    }
}
