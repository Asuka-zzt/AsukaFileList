package com.asuka.filelist.api.controller;

import com.asuka.filelist.application.auth.CurrentUserService;
import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.application.fs.FsDownloadTarget;
import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.user.User;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import com.asuka.filelist.infrastructure.security.DownloadSignService;
import com.asuka.filelist.infrastructure.security.InternalTokenGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

/**
 * 内部下载接口（仅 AI 服务调用）：用 master token 鉴权 + 短期签名校验，
 * 按归属用户重建只读上下文后流式输出网盘文件。
 *
 * <p>区别于 {@code /d/**}（需登录态）：AI 服务无用户 JWT，故走本接口。
 */
@RestController
public class InternalDownloadController {

    private final InternalTokenGuard internalTokenGuard;
    private final DownloadSignService downloadSignService;
    private final FsApplicationService fsApplicationService;
    private final UserApplicationService userApplicationService;
    private final CurrentUserService currentUserService;
    private final HttpClient proxyHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public InternalDownloadController(InternalTokenGuard internalTokenGuard,
                                      DownloadSignService downloadSignService,
                                      FsApplicationService fsApplicationService,
                                      UserApplicationService userApplicationService,
                                      CurrentUserService currentUserService) {
        this.internalTokenGuard = internalTokenGuard;
        this.downloadSignService = downloadSignService;
        this.fsApplicationService = fsApplicationService;
        this.userApplicationService = userApplicationService;
        this.currentUserService = currentUserService;
    }

    /**
     * 流式输出某用户路径下的文件，供 AI 服务索引下载。
     */
    @GetMapping("/internal/kb-download")
    public void download(@RequestParam String path,
                         @RequestParam long userId,
                         @RequestParam String sign,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        internalTokenGuard.verify(request);
        String normalized = PathUtils.fixAndCleanPath(path);
        if (!downloadSignService.verify(normalized, sign)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Invalid or expired download sign");
        }
        CurrentUser owner = rebuildOwnerContext(userId);
        LinkArgs args = new LinkArgs(request.getRemoteAddr(), Map.of(), "", false);
        FsDownloadTarget target = fsApplicationService.link(owner, normalized, args);
        serve(target, response);
    }

    // ─── 内部工具 ──────────────────────────────────────────────

    /** 按 userId 重建只读用户上下文（参考分享的 creator 上下文重建）。 */
    private CurrentUser rebuildOwnerContext(long userId) {
        User owner = userApplicationService.requireUser(userId);
        if (owner.disabled()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Document owner is disabled");
        }
        return currentUserService.toCurrentUser(owner);
    }

    /**
     * 全量输出文件：本地文件直接流；远程带鉴权头代理拉取；其余远程 302 重定向。
     */
    private void serve(FsDownloadTarget target, HttpServletResponse response) throws IOException {
        URI url = target.link().url();
        boolean remote = url.getScheme() != null && !"file".equalsIgnoreCase(url.getScheme());
        if (!remote) {
            response.setContentType("application/octet-stream");
            Files.copy(Paths.get(url), response.getOutputStream());
            response.getOutputStream().flush();
            return;
        }
        Map<String, String> headers = target.link().headers();
        if (headers != null && !headers.isEmpty()) {
            proxyRemote(url, headers, response);
            return;
        }
        response.sendRedirect(url.toString());
    }

    /** 带鉴权头代理拉取上游并流式回写。 */
    private void proxyRemote(URI url, Map<String, String> headers, HttpServletResponse response) throws IOException {
        HttpRequest.Builder upstream = HttpRequest.newBuilder(url).GET();
        headers.forEach(upstream::header);
        try {
            HttpResponse<InputStream> resp = proxyHttpClient.send(
                    upstream.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR,
                        "Upstream download failed with status " + resp.statusCode());
            }
            resp.headers().firstValue("Content-Type")
                    .ifPresent(v -> response.setHeader("Content-Type", v));
            try (InputStream in = resp.body()) {
                in.transferTo(response.getOutputStream());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Upstream download interrupted");
        }
    }
}
