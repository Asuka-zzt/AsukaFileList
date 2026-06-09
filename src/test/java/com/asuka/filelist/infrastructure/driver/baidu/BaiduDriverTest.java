package com.asuka.filelist.infrastructure.driver.baidu;

import com.asuka.filelist.domain.fs.BasicFileObject;
import com.asuka.filelist.domain.fs.FileLink;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.ListArgs;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 百度驱动：list JSON 解析、link 头注入、路径映射（本地 stub server）。
 */
class BaiduDriverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/2.0/token", json(
                "{\"access_token\":\"tok\",\"expires_in\":3600}"));
        server.createContext("/rest/2.0/xpan/file", json(
                "{\"errno\":0,\"list\":["
                + "{\"server_filename\":\"a.txt\",\"isdir\":0,\"size\":5,\"server_mtime\":1700000000,\"fs_id\":111,\"path\":\"/a.txt\"},"
                + "{\"server_filename\":\"sub\",\"isdir\":1,\"size\":0,\"server_mtime\":1700000001,\"fs_id\":222,\"path\":\"/sub\"}"
                + "]}"));
        server.createContext("/rest/2.0/xpan/multimedia", json(
                "{\"errno\":0,\"list\":[{\"dlink\":\"http://d.pcs.baidu.com/file?fid=111\"}]}"));
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * list 解析文件/目录、fs_id 入 id、path 映射为 actualPath。
     */
    @Test
    void list_parsesEntries() {
        BaiduDriver driver = initializedDriver("/");

        List<FileObject> children = driver.list(context(), root(), new ListArgs("/", false));

        assertThat(children).hasSize(2);
        FileObject file = children.get(0);
        assertThat(file.name()).isEqualTo("a.txt");
        assertThat(file.directory()).isFalse();
        assertThat(file.size()).isEqualTo(5);
        assertThat(file.id()).isEqualTo("111");
        assertThat(file.path()).isEqualTo("/a.txt");
        assertThat(children.get(1).directory()).isTrue();
        assertThat(children.get(1).path()).isEqualTo("/sub");
    }

    /**
     * link 返回 dlink+token，并注入百度 User-Agent 头。
     */
    @Test
    void link_injectsUserAgentHeader() {
        BaiduDriver driver = initializedDriver("/");
        FileObject file = new BasicFileObject("111", "/a.txt", "a.txt", 5,
                Instant.EPOCH, Instant.EPOCH, false, Map.of());

        FileLink link = driver.link(context(), file, new LinkArgs("", Map.of(), "", false));

        assertThat(link.url().toString()).contains("d.pcs.baidu.com").contains("access_token=tok");
        assertThat(link.headers()).containsEntry("User-Agent", "pan.baidu.com");
    }

    /**
     * rootPath 非根时 actualPath↔百度绝对路径互转。
     */
    @Test
    void pathMapping_withRootPath() {
        BaiduDriver driver = initializedDriver("/apps/demo");

        assertThat(driver.toBaiduPath("/")).isEqualTo("/apps/demo");
        assertThat(driver.toBaiduPath("/a/b.txt")).isEqualTo("/apps/demo/a/b.txt");
        assertThat(driver.toActualPath("/apps/demo")).isEqualTo("/");
        assertThat(driver.toActualPath("/apps/demo/a/b.txt")).isEqualTo("/a/b.txt");
    }

    private BaiduDriver initializedDriver(String rootPath) {
        BaiduDriver driver = new BaiduDriver(objectMapper, httpClient);
        driver.setEndpointsForTest(baseUrl(), baseUrl());
        driver.setStorage(storage(rootPath));
        driver.init(context());
        return driver;
    }

    private FileObject root() {
        return new BasicFileObject("", "/", "/", 0, Instant.EPOCH, Instant.EPOCH, true, Map.of());
    }

    private Storage storage(String rootPath) {
        String addition = "{\"refreshToken\":\"r\",\"clientId\":\"c\",\"clientSecret\":\"s\",\"rootPath\":\""
                + rootPath + "\"}";
        return new Storage(1L, "/baidu", 0, "BaiduNetdisk", 30, "work",
                addition, "", null,
                false, false, false, "name", "asc", "front", false, "proxy", false);
    }

    private DriverContext context() {
        return new DriverContext("/baidu", Map.of());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private com.sun.net.httpserver.HttpHandler json(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };
    }
}
