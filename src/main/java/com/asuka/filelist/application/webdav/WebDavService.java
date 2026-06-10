package com.asuka.filelist.application.webdav;

import com.asuka.filelist.api.request.FsGetRequest;
import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.application.fs.FsDownloadTarget;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebDAV ↔ 统一 VFS 适配层：把 WebDAV 语义映射到 {@link FsApplicationService}，
 * 复用其权限校验、basePath 与路径夹紧。不直接碰驱动。
 */
@Service
public class WebDavService {

    private final FsApplicationService fsApplicationService;

    public WebDavService(FsApplicationService fsApplicationService) {
        this.fsApplicationService = fsApplicationService;
    }

    /**
     * 查询单个路径的资源属性（PROPFIND Depth 0 / 自身条目）。
     *
     * <p>根 `/` 与多段挂载点的中间虚拟目录在 VFS 中没有真实对象，回退为虚拟集合。
     */
    public DavResource stat(CurrentUser user, String davPath) {
        String path = PathUtils.fixAndCleanPath(davPath);
        if ("/".equals(path)) {
            return virtualCollection(path);
        }
        try {
            return toResource(fsApplicationService.get(user, new FsGetRequest(path, null)));
        } catch (BusinessException ex) {
            // 虚拟中间目录（挂载点尚未到达真实存储）：能列出即视为集合
            if (isVirtualDir(user, path)) {
                return virtualCollection(path);
            }
            throw ex;
        }
    }

    /**
     * 列出集合下的子资源（PROPFIND Depth 1）。
     */
    public List<DavResource> listChildren(CurrentUser user, String davPath) {
        FsListResponse listed = fsApplicationService.list(user,
                new FsListRequest(davPath, null, false, 1, -1));
        return listed.content().stream().map(this::toResource).toList();
    }

    /**
     * 解析下载目标（GET）。
     */
    public FsDownloadTarget openFile(CurrentUser user, String davPath, String clientIp) {
        return fsApplicationService.link(user, davPath, new LinkArgs(clientIp, Map.of(), "", false));
    }

    // ─── 内部辅助 ───────────────────────────────────────────────

    /**
     * 路径能否被列出（即虚拟中间目录或真实目录）。
     */
    private boolean isVirtualDir(CurrentUser user, String path) {
        try {
            fsApplicationService.list(user, new FsListRequest(path, null, false, 1, 1));
            return true;
        } catch (BusinessException ignore) {
            return false;
        }
    }

    private DavResource virtualCollection(String path) {
        return new DavResource(path, nameOf(path), true, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    private DavResource toResource(FileObjectResponse f) {
        return new DavResource(f.path(), f.name(), f.isDir(), f.size(), f.modified(), f.created());
    }

    private String nameOf(String davPath) {
        String clean = PathUtils.fixAndCleanPath(davPath);
        if ("/".equals(clean)) {
            return "";
        }
        return clean.substring(clean.lastIndexOf('/') + 1);
    }
}
