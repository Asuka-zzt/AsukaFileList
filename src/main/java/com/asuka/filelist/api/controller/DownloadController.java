package com.asuka.filelist.api.controller;

import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.application.fs.FsDownloadTarget;
import com.asuka.filelist.application.meta.MetaApplicationService;
import com.asuka.filelist.application.share.ShareApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.common.util.FileTypeUtils;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import com.asuka.filelist.infrastructure.security.DownloadSignService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

/**
 * 文件下载接口，处理 /d/** 路径，支持 HTTP Range 分段下载。
 * M4 依赖登录态读权限；下载签名在 M5 引入。
 */
@RestController
public class DownloadController {

    private static final String DOWNLOAD_PREFIX = "/d";
    private static final String SHARE_PREFIX = "/sd";
    private static final int BUFFER_SIZE = 8192;

    private final FsApplicationService fsApplicationService;
    private final MetaApplicationService metaApplicationService;
    private final DownloadSignService downloadSignService;
    private final ShareApplicationService shareApplicationService;
    private final HttpClient proxyHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DownloadController(FsApplicationService fsApplicationService,
                              MetaApplicationService metaApplicationService,
                              DownloadSignService downloadSignService,
                              ShareApplicationService shareApplicationService) {
        this.fsApplicationService = fsApplicationService;
        this.metaApplicationService = metaApplicationService;
        this.downloadSignService = downloadSignService;
        this.shareApplicationService = shareApplicationService;
    }

    /**
     * 下载文件，按 Range 头返回 200 全量或 206 分段。
     */
    @GetMapping("/d/**")
    public void download(CurrentUser currentUser, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = extractPath(request);
        enforceSign(currentUser, path, request.getParameter("sign"));
        LinkArgs args = new LinkArgs(request.getRemoteAddr(), Map.of(), "", false);
        FsDownloadTarget target = fsApplicationService.link(currentUser, path, args);
        serveTarget(target, request, response);
    }

    /**
     * M7: 分享下载 /sd/{shareId}/**，凭 ?token= 校验后输出，免登录。
     */
    @GetMapping("/sd/{shareId}/**")
    public void shareDownload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String tail = extractShareTail(request);
        int slash = tail.indexOf('/');
        String shareId = slash < 0 ? tail : tail.substring(0, slash);
        String subPath = slash < 0 ? "/" : tail.substring(slash);
        LinkArgs args = new LinkArgs(request.getRemoteAddr(), Map.of(), "", false);
        FsDownloadTarget target = shareApplicationService.linkPublic(
                shareId, request.getParameter("token"), subPath, args);
        serveTarget(target, request, response);
    }

    /**
     * 按驱动链接输出：
     * <ul>
     *   <li>本地 file 协议 → 分段流式回写；</li>
     *   <li>远程且 link.headers 非空（如百度需 User-Agent）→ 服务端带头代理流式拉取；</li>
     *   <li>其余远程（如 S3 预签名）→ 302 重定向。</li>
     * </ul>
     */
    private void serveTarget(FsDownloadTarget target, HttpServletRequest request, HttpServletResponse response) throws IOException {
        URI url = target.link().url();
        boolean remote = url.getScheme() != null && !"file".equalsIgnoreCase(url.getScheme());
        if (!remote) {
            serveLocalFile(Paths.get(url), target.file().name(), request, response);
            return;
        }
        Map<String, String> headers = target.link().headers();
        if (headers != null && !headers.isEmpty()) {
            proxyRemote(url, headers, request, response);
            return;
        }
        response.sendRedirect(url.toString());
    }

    /**
     * 带鉴权头代理拉取上游并流式回写，透传 Range 与上游 Content-Range/Length/Type。
     */
    void proxyRemote(URI url, Map<String, String> headers,
                     HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpRequest.Builder upstream = HttpRequest.newBuilder(url).GET();
        headers.forEach(upstream::header);
        String range = request.getHeader("Range");
        if (range != null && !range.isBlank()) {
            upstream.header("Range", range);
        }
        try {
            HttpResponse<InputStream> resp = proxyHttpClient.send(
                    upstream.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = resp.statusCode();
            if (status / 100 != 2) {
                throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR,
                        "Upstream download failed with status " + status);
            }
            response.setStatus(status);
            copyHeader(resp, response, "Content-Type");
            copyHeader(resp, response, "Content-Length");
            copyHeader(resp, response, "Content-Range");
            copyHeader(resp, response, "Accept-Ranges");
            try (InputStream in = resp.body()) {
                in.transferTo(response.getOutputStream());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Upstream download interrupted");
        }
    }

    /**
     * 透传上游响应头到下游（存在时）。
     */
    private void copyHeader(HttpResponse<?> upstream, HttpServletResponse response, String name) {
        upstream.headers().firstValue(name).ifPresent(value -> response.setHeader(name, value));
    }

    /**
     * 提取 /sd 之后的 {shareId}/子路径 并解码。
     */
    private String extractShareTail(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String encoded = uri.length() > SHARE_PREFIX.length() + 1 ? uri.substring(SHARE_PREFIX.length() + 1) : "";
        return UriUtils.decode(encoded, StandardCharsets.UTF_8);
    }

    /**
     * M5: 需签名时校验 ?sign=。需签名条件 = 全局 signAll 或该路径命中带密码的 Meta；admin 豁免。
     * 签名绑定规范化后的用户可见路径，与 list/get 下发的 sign 一致。
     */
    private void enforceSign(CurrentUser currentUser, String visiblePath, String sign) {
        if (currentUser.admin()) {
            return;
        }
        String normalized = PathUtils.fixAndCleanPath(visiblePath);
        String internalPath = PathUtils.joinBasePath(currentUser.basePath(), normalized);
        boolean needsSign = downloadSignService.signAll()
                || metaApplicationService.resolve(internalPath).hasPassword();
        if (needsSign && !downloadSignService.verify(normalized, sign)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Valid download sign is required");
        }
    }

    /**
     * 从请求 URI 提取 /d 之后的用户可见路径并解码。
     */
    private String extractPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String encoded = uri.length() > DOWNLOAD_PREFIX.length() ? uri.substring(DOWNLOAD_PREFIX.length()) : "/";
        return UriUtils.decode(encoded, StandardCharsets.UTF_8);
    }

    /**
     * 输出本地文件，处理 Range 与下载头。
     */
    private void serveLocalFile(Path filePath, String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        long total = Files.size(filePath);
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(FileTypeUtils.guessContentType(fileName));
        response.setHeader("Content-Disposition", contentDisposition(fileName));

        long[] range = parseRange(request.getHeader("Range"), total);
        if (request.getHeader("Range") != null && range == null) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + total);
            return;
        }
        if (range == null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(total);
            try (InputStream in = Files.newInputStream(filePath)) {
                in.transferTo(response.getOutputStream());
            }
            return;
        }
        long start = range[0];
        long length = range[1] - range[0] + 1;
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Range", "bytes " + start + "-" + range[1] + "/" + total);
        response.setContentLengthLong(length);
        try (InputStream in = Files.newInputStream(filePath)) {
            in.skipNBytes(start);
            copyExact(in, response.getOutputStream(), length);
        }
    }

    /**
     * 构造兼容非 ASCII 文件名的 Content-Disposition。
     */
    private String contentDisposition(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }

    /**
     * 解析单个 Range，非法或越界返回 null；仅取首个范围。
     */
    private long[] parseRange(String header, long total) {
        if (header == null || !header.startsWith("bytes=")) {
            return null;
        }
        String spec = header.substring("bytes=".length()).trim();
        if (spec.contains(",")) {
            spec = spec.substring(0, spec.indexOf(','));
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startStr = spec.substring(0, dash).trim();
        String endStr = spec.substring(dash + 1).trim();
        try {
            long start;
            long end;
            if (startStr.isEmpty()) {
                long suffix = Long.parseLong(endStr);
                if (suffix <= 0) {
                    return null;
                }
                start = Math.max(0, total - suffix);
                end = total - 1;
            } else {
                start = Long.parseLong(startStr);
                end = endStr.isEmpty() ? total - 1 : Long.parseLong(endStr);
            }
            if (start > end || start >= total) {
                return null;
            }
            return new long[]{start, Math.min(end, total - 1)};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 从流中精确拷贝指定字节数到输出。
     */
    private void copyExact(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                break;
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }
}
