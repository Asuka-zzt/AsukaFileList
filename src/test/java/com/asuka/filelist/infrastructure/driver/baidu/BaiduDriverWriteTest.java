package com.asuka.filelist.infrastructure.driver.baidu;

import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.UploadFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 百度写能力：put（precreate→superfile2→create）、mkdir、filemanager（delete/rename/move/copy）。
 * 用本地 stub server 记录请求并按 method 应答，验证请求拼装与流程（无需真实账号）。
 */
class BaiduDriverWriteTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpServer server;
    private final List<String> calls = Collections.synchronizedList(new java.util.ArrayList<>());
    private final Map<String, String> forms = new ConcurrentHashMap<>();
    private final AtomicReference<byte[]> uploadedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/2.0/token", exchange ->
                respond(exchange, "{\"access_token\":\"tok\",\"expires_in\":3600}"));
        server.createContext("/rest/2.0/xpan/file", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String method = param(query, "method");
            String opera = param(query, "opera");
            String key = opera == null ? method : method + ":" + opera;
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(key);
            if (!body.isBlank()) {
                forms.put(key, body);
            }
            respond(exchange, switch (method) {
                case "list" -> "{\"errno\":0,\"list\":[{\"server_filename\":\"up.txt\",\"isdir\":0,"
                        + "\"size\":5,\"server_mtime\":1700000000,\"fs_id\":999,\"path\":\"/up.txt\"}]}";
                case "precreate" -> "{\"errno\":0,\"uploadid\":\"UP123\",\"block_list\":[0]}";
                case "create" -> "{\"errno\":0,\"fs_id\":999,\"path\":\"/up.txt\",\"isdir\":0}";
                case "filemanager" -> "{\"errno\":0,\"info\":[]}";
                default -> "{\"errno\":0}";
            });
        });
        server.createContext("/rest/2.0/pcs/superfile2", exchange -> {
            uploadedBody.set(exchange.getRequestBody().readAllBytes());
            calls.add("superfile2");
            respond(exchange, "{\"md5\":\"abc\"}");
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * 上传走 precreate → superfile2（含分块内容）→ create，返回的对象来自随后的 list。
     */
    @Test
    void put_runsThreeStepUpload() {
        BaiduDriver driver = driver();

        FileObject result = driver.put(context(), "/",
                new UploadFile("up.txt", 5, "text/plain",
                        new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))));

        assertThat(calls).contains("precreate", "superfile2", "create");
        assertThat(new String(uploadedBody.get(), StandardCharsets.UTF_8)).contains("hello");
        assertThat(forms.get("precreate")).contains("rtype=3").contains("block_list=");
        assertThat(result.name()).isEqualTo("up.txt");
    }

    /**
     * mkdir 走 create&isdir=1。
     */
    @Test
    void mkdir_callsCreateIsdir() {
        BaiduDriver driver = driver();

        FileObject dir = driver.mkdir(context(), "/", "newdir");

        assertThat(calls).contains("create");
        assertThat(forms.get("create")).contains("isdir=1");
        assertThat(dir.directory()).isTrue();
        assertThat(dir.name()).isEqualTo("newdir");
    }

    /**
     * delete/rename/move/copy 走 filemanager 对应 opera（同步）。
     */
    @Test
    void filemanagerOps_useCorrectOpera() {
        BaiduDriver driver = driver();

        driver.remove(context(), "/up.txt");
        driver.rename(context(), "/up.txt", "renamed.txt");
        driver.move(context(), "/up.txt", "/dst");
        driver.copy(context(), "/up.txt", "/dst");

        assertThat(calls).contains("filemanager:delete", "filemanager:rename",
                "filemanager:move", "filemanager:copy");
        assertThat(forms.get("filemanager:delete")).contains("async=0").contains("filelist=");
        assertThat(forms.get("filemanager:move")).contains("filelist=");
    }

    private BaiduDriver driver() {
        BaiduDriver driver = new BaiduDriver(objectMapper, httpClient);
        driver.setEndpointsForTest(baseUrl(), baseUrl(), baseUrl());
        driver.setStorage(storage());
        driver.init(context());
        return driver;
    }

    private Storage storage() {
        return new Storage(1L, "/baidu", 0, "BaiduNetdisk", 30, "work",
                "{\"refreshToken\":\"r\",\"clientId\":\"c\",\"clientSecret\":\"s\",\"rootPath\":\"/\"}",
                "", null, false, false, false, "name", "asc", "front", false, "proxy", false);
    }

    private DriverContext context() {
        return new DriverContext("/baidu", Map.of());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String param(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            if (name.equals(key)) {
                return eq < 0 ? "" : pair.substring(eq + 1);
            }
        }
        return null;
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
