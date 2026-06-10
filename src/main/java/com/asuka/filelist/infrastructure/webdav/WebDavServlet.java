package com.asuka.filelist.infrastructure.webdav;

import com.asuka.filelist.application.fs.FsDownloadTarget;
import com.asuka.filelist.application.webdav.DavResource;
import com.asuka.filelist.application.webdav.WebDavService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.infrastructure.driver.UploadFile;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * WebDAV 服务端入口，挂载在 {@code /dav/*}。独立于 DispatcherServlet，故不经 MVC 的
 * Bearer 拦截器；自带 Digest 鉴权。批次 2 实现只读：OPTIONS / PROPFIND / GET / HEAD。
 */
public class WebDavServlet extends HttpServlet {

    static final String DAV_PREFIX = "/dav";

    private final transient WebDavService webDavService;
    private final transient WebDavDigestAuthenticator authenticator;
    private final transient WebDavStreaming streaming;
    private final transient WebDavLockManager lockManager;

    public WebDavServlet(WebDavService webDavService, WebDavDigestAuthenticator authenticator,
                         WebDavStreaming streaming, WebDavLockManager lockManager) {
        this.webDavService = webDavService;
        this.authenticator = authenticator;
        this.streaming = streaming;
        this.lockManager = lockManager;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<CurrentUser> user = authenticator.authenticate(request);
        if (user.isEmpty()) {
            authenticator.sendChallenge(response);
            return;
        }
        String method = request.getMethod();
        String davPath = davPath(request);
        try {
            switch (method.toUpperCase()) {
                case "OPTIONS" -> handleOptions(response);
                case "PROPFIND" -> handlePropfind(user.get(), davPath, request, response);
                case "GET" -> handleGet(user.get(), davPath, request, response, false);
                case "HEAD" -> handleGet(user.get(), davPath, request, response, true);
                case "PUT" -> handlePut(user.get(), davPath, request, response);
                case "MKCOL" -> handleMkcol(user.get(), davPath, response);
                case "DELETE" -> handleDelete(user.get(), davPath, response);
                case "MOVE" -> handleMoveCopy(user.get(), davPath, request, response, true);
                case "COPY" -> handleMoveCopy(user.get(), davPath, request, response, false);
                case "LOCK" -> handleLock(davPath, response);
                case "UNLOCK" -> handleUnlock(davPath, request, response);
                case "PROPPATCH" -> handleProppatch(user.get(), davPath, response);
                default -> response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (BusinessException ex) {
            response.sendError(ex.errorCode().httpStatus().value(), ex.getMessage());
        }
    }

    /**
     * OPTIONS：声明 DAV 能力（class 1+2，含 LOCK）与允许方法。
     */
    private void handleOptions(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("DAV", "1, 2");
        response.setHeader("MS-Author-Via", "DAV");
        response.setHeader("Allow",
                "OPTIONS, GET, HEAD, PROPFIND, PUT, MKCOL, DELETE, MOVE, COPY, LOCK, UNLOCK, PROPPATCH");
        response.setContentLength(0);
    }

    /**
     * PROPFIND：返回目标自身（Depth 0）或自身 + 一级子项（Depth 1）的 207 multistatus。
     */
    private void handlePropfind(CurrentUser user, String davPath, HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        DavResource self = webDavService.stat(user, davPath);
        List<DavResource> entries = new ArrayList<>();
        entries.add(self);
        if (self.collection() && !"0".equals(depth(request))) {
            entries.addAll(webDavService.listChildren(user, davPath));
        }
        String xml = WebDavXml.multiStatus(DAV_PREFIX, entries);
        response.setStatus(207);
        response.setContentType("application/xml; charset=UTF-8");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    /**
     * GET/HEAD：输出文件内容（目录不可下载，VFS 映射为 400）。
     */
    private void handleGet(CurrentUser user, String davPath, HttpServletRequest request,
                           HttpServletResponse response, boolean headOnly) throws IOException {
        FsDownloadTarget target = webDavService.openFile(user, davPath, request.getRemoteAddr());
        streaming.serve(target, request, response, headOnly);
    }

    /**
     * PUT：流式写入文件，父目录须存在。
     */
    private void handlePut(CurrentUser user, String davPath, HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        long size = request.getContentLengthLong();
        String contentType = request.getContentType();
        UploadFile file = new UploadFile(nameOf(davPath), size, contentType, request.getInputStream());
        webDavService.put(user, davPath, file);
        response.setStatus(HttpServletResponse.SC_CREATED);
    }

    /**
     * MKCOL：新建集合。
     */
    private void handleMkcol(CurrentUser user, String davPath, HttpServletResponse response) {
        webDavService.mkcol(user, davPath);
        response.setStatus(HttpServletResponse.SC_CREATED);
    }

    /**
     * DELETE：删除资源。
     */
    private void handleDelete(CurrentUser user, String davPath, HttpServletResponse response) {
        webDavService.remove(user, davPath);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /**
     * MOVE/COPY：解析 Destination；Overwrite=F 且目标存在 → 412。
     */
    private void handleMoveCopy(CurrentUser user, String srcPath, HttpServletRequest request,
                                HttpServletResponse response, boolean move) throws IOException {
        String dstPath = destinationPath(request);
        if (dstPath == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid Destination");
            return;
        }
        if (!overwriteAllowed(request) && exists(user, dstPath)) {
            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED, "Destination exists");
            return;
        }
        if (move) {
            webDavService.move(user, srcPath, dstPath);
        } else {
            webDavService.copy(user, srcPath, dstPath);
        }
        response.setStatus(HttpServletResponse.SC_CREATED);
    }

    /**
     * LOCK：发放/续期假锁，回 lockdiscovery 与 Lock-Token 头。
     */
    private void handleLock(String davPath, HttpServletResponse response) throws IOException {
        WebDavLockManager.Lock lock = lockManager.lock(davPath);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Lock-Token", "<" + lock.token() + ">");
        response.setContentType("application/xml; charset=UTF-8");
        byte[] body = WebDavXml.lockDiscovery(lock.token(), lockManager.timeoutSeconds())
                .getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    /**
     * UNLOCK：释放锁。
     */
    private void handleUnlock(String davPath, HttpServletRequest request, HttpServletResponse response) {
        boolean released = lockManager.unlock(davPath, request.getHeader("Lock-Token"));
        response.setStatus(released ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CONFLICT);
    }

    /**
     * PROPPATCH：不持久化自定义属性，回 207 标记成功以兼容 Windows 写时间戳等。
     */
    private void handleProppatch(CurrentUser user, String davPath, HttpServletResponse response) throws IOException {
        DavResource self = webDavService.stat(user, davPath);
        String href = WebDavXml.href(DAV_PREFIX, self);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<D:multistatus xmlns:D=\"DAV:\"><D:response><D:href>" + href + "</D:href>"
                + "<D:propstat><D:prop/><D:status>HTTP/1.1 200 OK</D:status></D:propstat>"
                + "</D:response></D:multistatus>\n";
        response.setStatus(207);
        response.setContentType("application/xml; charset=UTF-8");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    /**
     * 资源是否存在（用于 Overwrite 判定）。
     */
    private boolean exists(CurrentUser user, String davPath) {
        try {
            webDavService.stat(user, davPath);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    /**
     * 解析 Destination 头为 /dav 之后的可见路径；非本服务前缀返回 null。
     */
    private String destinationPath(HttpServletRequest request) {
        String dest = request.getHeader("Destination");
        if (dest == null || dest.isBlank()) {
            return null;
        }
        int idx = dest.indexOf(DAV_PREFIX + "/");
        String tail;
        if (idx >= 0) {
            tail = dest.substring(idx + DAV_PREFIX.length());
        } else if (dest.endsWith(DAV_PREFIX)) {
            tail = "/";
        } else {
            return null;
        }
        return PathUtils.fixAndCleanPath(URLDecoder.decode(tail, StandardCharsets.UTF_8));
    }

    private boolean overwriteAllowed(HttpServletRequest request) {
        String overwrite = request.getHeader("Overwrite");
        return overwrite == null || !"F".equalsIgnoreCase(overwrite.trim());
    }

    private String nameOf(String davPath) {
        String clean = PathUtils.fixAndCleanPath(davPath);
        if ("/".equals(clean)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Operation requires a named resource");
        }
        return clean.substring(clean.lastIndexOf('/') + 1);
    }

    /**
     * 取 /dav 之后的用户可见路径（servlet 已解码 pathInfo）。
     */
    private String davPath(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        return PathUtils.fixAndCleanPath(pathInfo == null ? "/" : pathInfo);
    }

    private String depth(HttpServletRequest request) {
        String depth = request.getHeader("Depth");
        return depth == null ? "1" : depth.trim();
    }
}
