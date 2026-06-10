package com.asuka.filelist.infrastructure.webdav;

import com.asuka.filelist.application.fs.FsDownloadTarget;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.util.FileTypeUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

/**
 * WebDAV GET/HEAD 输出：本地 file 协议直读（支持 Range），远程驱动一律服务端代理
 * （WebDAV 客户端不跟随 302），透传 Range 与上游 Content-Range/Length/Type。
 */
@Component
public class WebDavStreaming {

    private static final int BUFFER_SIZE = 8192;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 输出下载目标；headOnly=true 时只写头不写体。
     */
    public void serve(FsDownloadTarget target, HttpServletRequest request, HttpServletResponse response,
                      boolean headOnly) throws IOException {
        URI url = target.link().url();
        boolean remote = url.getScheme() != null && !"file".equalsIgnoreCase(url.getScheme());
        if (remote) {
            proxy(url, target.link().headers(), request, response, headOnly);
        } else {
            serveLocal(Paths.get(url), target.file().name(), request, response, headOnly);
        }
    }

    private void serveLocal(Path filePath, String fileName, HttpServletRequest request,
                            HttpServletResponse response, boolean headOnly) throws IOException {
        long total = Files.size(filePath);
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(FileTypeUtils.guessContentType(fileName));

        long[] range = parseRange(request.getHeader("Range"), total);
        if (request.getHeader("Range") != null && range == null) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + total);
            return;
        }
        if (range == null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(total);
            if (headOnly) {
                return;
            }
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
        if (headOnly) {
            return;
        }
        try (InputStream in = Files.newInputStream(filePath)) {
            in.skipNBytes(start);
            copyExact(in, response.getOutputStream(), length);
        }
    }

    private void proxy(URI url, Map<String, String> headers, HttpServletRequest request,
                       HttpServletResponse response, boolean headOnly) throws IOException {
        HttpRequest.Builder upstream = HttpRequest.newBuilder(url).GET();
        if (headers != null) {
            headers.forEach(upstream::header);
        }
        String range = request.getHeader("Range");
        if (range != null && !range.isBlank()) {
            upstream.header("Range", range);
        }
        try {
            HttpResponse<InputStream> resp = httpClient.send(upstream.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = resp.statusCode();
            if (status / 100 != 2) {
                resp.body().close();
                throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Upstream download failed with status " + status);
            }
            response.setStatus(status);
            copyHeader(resp, response, "Content-Type");
            copyHeader(resp, response, "Content-Length");
            copyHeader(resp, response, "Content-Range");
            copyHeader(resp, response, "Accept-Ranges");
            try (InputStream in = resp.body()) {
                if (!headOnly) {
                    in.transferTo(response.getOutputStream());
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Upstream download interrupted");
        }
    }

    private void copyHeader(HttpResponse<?> upstream, HttpServletResponse response, String name) {
        upstream.headers().firstValue(name).ifPresent(value -> response.setHeader(name, value));
    }

    /**
     * 解析单个 Range，非法或越界返回 null。
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
