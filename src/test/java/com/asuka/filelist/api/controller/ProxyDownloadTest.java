package com.asuka.filelist.api.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代理下载分支：UA 头透传、Range 透传、上游状态/头回写（本地 stub 上游）。
 */
class ProxyDownloadTest {

    private HttpServer server;
    private final AtomicReference<String> seenUserAgent = new AtomicReference<>();
    private final AtomicReference<String> seenRange = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            seenUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            String range = exchange.getRequestHeaders().getFirst("Range");
            seenRange.set(range);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            byte[] full = "baidu-bytes".getBytes(StandardCharsets.UTF_8);
            if (range != null) {
                byte[] part = "baidu".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Range", "bytes 0-4/" + full.length);
                exchange.sendResponseHeaders(206, part.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(part);
                }
            } else {
                exchange.sendResponseHeaders(200, full.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(full);
                }
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * 无 Range：注入 UA 头并回写 200 全量。
     */
    @Test
    void proxy_forwardsUserAgentAndBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller().proxyRemote(fileUri(), Map.of("User-Agent", "pan.baidu.com"), request, response);

        assertThat(seenUserAgent.get()).isEqualTo("pan.baidu.com");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("baidu-bytes");
    }

    /**
     * 带 Range：透传到上游并回写 206 + Content-Range。
     */
    @Test
    void proxy_forwardsRange() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Range", "bytes=0-4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller().proxyRemote(fileUri(), Map.of("User-Agent", "pan.baidu.com"), request, response);

        assertThat(seenRange.get()).isEqualTo("bytes=0-4");
        assertThat(response.getStatus()).isEqualTo(206);
        assertThat(response.getHeader("Content-Range")).isEqualTo("bytes 0-4/11");
        assertThat(response.getContentAsString()).isEqualTo("baidu");
    }

    private DownloadController controller() {
        return new DownloadController(null, null, null, null);
    }

    private URI fileUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file");
    }
}
