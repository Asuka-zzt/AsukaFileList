package com.asuka.filelist.infrastructure.driver.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 百度 token 管理：缓存命中与过期刷新（本地 stub server，无需真实账号）。
 */
class BaiduTokenManagerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile long expiresIn = 3600;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/2.0/token", exchange -> {
            calls.incrementAndGet();
            byte[] body = ("{\"access_token\":\"tok-" + calls.get() + "\",\"expires_in\":" + expiresIn + "}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * 未过期时第二次取 token 命中缓存，不再请求上游。
     */
    @Test
    void cachesTokenWhileValid() {
        BaiduTokenManager manager = manager();

        String first = manager.getAccessToken();
        String second = manager.getAccessToken();

        assertThat(first).isEqualTo("tok-1");
        assertThat(second).isEqualTo("tok-1");
        assertThat(calls.get()).isEqualTo(1);
    }

    /**
     * 有效期过短（扣除 skew 后即过期）时每次都刷新。
     */
    @Test
    void refreshesWhenExpired() {
        expiresIn = 30; // 30 - 60s skew < 0 → 立即过期
        BaiduTokenManager manager = manager();

        String first = manager.getAccessToken();
        String second = manager.getAccessToken();

        assertThat(first).isEqualTo("tok-1");
        assertThat(second).isEqualTo("tok-2");
        assertThat(calls.get()).isEqualTo(2);
    }

    private BaiduTokenManager manager() {
        BaiduDriverAddition addition = new BaiduDriverAddition("refresh", "cid", "secret", "/");
        return new BaiduTokenManager(httpClient, objectMapper, addition, baseUrl());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
