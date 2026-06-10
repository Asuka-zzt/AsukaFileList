package com.asuka.filelist.application.webdav;

import com.asuka.filelist.api.request.FsCopyRequest;
import com.asuka.filelist.api.request.FsGetRequest;
import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.request.FsMkdirRequest;
import com.asuka.filelist.api.request.FsMoveRequest;
import com.asuka.filelist.api.request.FsRemoveRequest;
import com.asuka.filelist.api.request.FsRenameRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.application.fs.FsDownloadTarget;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.UploadFile;
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

    // ─── 写操作（MKCOL/PUT/DELETE/MOVE/COPY）────────────────────

    /**
     * 新建目录（MKCOL）。
     */
    public void mkcol(CurrentUser user, String davPath) {
        fsApplicationService.mkdir(user, new FsMkdirRequest(davPath));
    }

    /**
     * 上传文件（PUT）；目标父目录须存在。
     */
    public void put(CurrentUser user, String davPath, UploadFile file) {
        fsApplicationService.put(user, parentOf(davPath), file);
    }

    /**
     * 删除文件或目录（DELETE）。
     */
    public void remove(CurrentUser user, String davPath) {
        fsApplicationService.remove(user, new FsRemoveRequest(parentOf(davPath), List.of(nameOf(davPath))));
    }

    /**
     * 移动（MOVE）：同目录退化为重命名（Windows F2）；跨目录移动后名不同再重命名。
     */
    public void move(CurrentUser user, String srcPath, String dstPath) {
        String srcDir = parentOf(srcPath);
        String dstDir = parentOf(dstPath);
        String srcName = nameOf(srcPath);
        String dstName = nameOf(dstPath);
        if (srcDir.equals(dstDir)) {
            if (!srcName.equals(dstName)) {
                fsApplicationService.rename(user, new FsRenameRequest(srcPath, dstName));
            }
            return;
        }
        fsApplicationService.move(user, new FsMoveRequest(srcDir, dstDir, List.of(srcName)));
        renameIfNeeded(user, dstDir, srcName, dstName);
    }

    /**
     * 复制（COPY）：跨目录复制后名不同再重命名。
     *
     * <p>同目录复制为新名（文件副本）无对应 VFS 原语，暂不支持（映射为 DRIVER_NOT_SUPPORTED）。
     */
    public void copy(CurrentUser user, String srcPath, String dstPath) {
        String srcDir = parentOf(srcPath);
        String dstDir = parentOf(dstPath);
        String srcName = nameOf(srcPath);
        if (srcDir.equals(dstDir)) {
            throw new BusinessException(ErrorCode.DRIVER_NOT_SUPPORTED,
                    "Same-directory copy to a new name is not supported");
        }
        fsApplicationService.copy(user, new FsCopyRequest(srcDir, dstDir, List.of(srcName)));
        renameIfNeeded(user, dstDir, srcName, nameOf(dstPath));
    }

    /**
     * VFS 的 move/copy 保留原名，目标名不同时补一次 rename。
     */
    private void renameIfNeeded(CurrentUser user, String dstDir, String srcName, String dstName) {
        if (dstName != null && !dstName.isEmpty() && !dstName.equals(srcName)) {
            String moved = "/".equals(dstDir) ? "/" + srcName : dstDir + "/" + srcName;
            fsApplicationService.rename(user, new FsRenameRequest(moved, dstName));
        }
    }

    // ─── 内部辅助 ───────────────────────────────────────────────

    private String parentOf(String davPath) {
        String clean = PathUtils.fixAndCleanPath(davPath);
        int idx = clean.lastIndexOf('/');
        return idx <= 0 ? "/" : clean.substring(0, idx);
    }

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
