package com.asuka.filelist.infrastructure.webdav;

import com.asuka.filelist.application.fs.FsDownloadTarget;
import com.asuka.filelist.application.webdav.DavResource;
import com.asuka.filelist.application.webdav.WebDavService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
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

    public WebDavServlet(WebDavService webDavService, WebDavDigestAuthenticator authenticator,
                         WebDavStreaming streaming) {
        this.webDavService = webDavService;
        this.authenticator = authenticator;
        this.streaming = streaming;
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
                default -> response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (BusinessException ex) {
            response.sendError(ex.errorCode().httpStatus().value(), ex.getMessage());
        }
    }

    /**
     * OPTIONS：声明 DAV 能力与允许方法（批次 2 只读）。
     */
    private void handleOptions(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("DAV", "1");
        response.setHeader("MS-Author-Via", "DAV");
        response.setHeader("Allow", "OPTIONS, GET, HEAD, PROPFIND");
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
