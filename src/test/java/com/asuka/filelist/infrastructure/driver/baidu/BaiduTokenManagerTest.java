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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final List<String> seenRefreshTokens = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/2.0/token", exchange -> {
            int n = calls.incrementAndGet();
            seenRefreshTokens.add(queryParam(exchange.getRequestURI().getRawQuery(), "refresh_token"));
            // 百度轮换：每次返回新的 refresh_token
            byte[] body = ("{\"access_token\":\"tok-" + n + "\",\"expires_in\":" + expiresIn
                    + ",\"refresh_token\":\"newrt-" + n + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    private static String queryParam(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
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

    /**
     * 百度轮换 refresh_token：每次刷新用上一次返回的新 token，并回调持久化。
     */
    @Test
    void rotatesAndPersistsRefreshToken() {
        expiresIn = 30; // 立即过期，强制每次刷新
        List<String> persisted = Collections.synchronizedList(new ArrayList<>());
        BaiduDriverAddition addition = new BaiduDriverAddition("refresh", "cid", "secret", "/");
        BaiduTokenManager manager = new BaiduTokenManager(
                httpClient, objectMapper, addition, baseUrl(), persisted::add);

        manager.getAccessToken();
        manager.getAccessToken();

        // 首次用原始 token，二次用上次轮换出的新 token
        assertThat(seenRefreshTokens).containsExactly("refresh", "newrt-1");
        assertThat(persisted).containsExactly("newrt-1", "newrt-2");
    }

    private BaiduTokenManager manager() {
        BaiduDriverAddition addition = new BaiduDriverAddition("refresh", "cid", "secret", "/");
        return new BaiduTokenManager(httpClient, objectMapper, addition, baseUrl());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
