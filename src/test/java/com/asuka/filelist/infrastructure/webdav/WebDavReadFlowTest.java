package com.asuka.filelist.infrastructure.webdav;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebDAV 只读闭环（真实端口 + Digest 客户端）：401 challenge、PROPFIND 列存储/文件、GET 取内容。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebDavReadFlowTest {

    private static final String USER = "admin";
    private static final String PASS = "test-admin-password";
    private static final String DAV_PASS = "davpass1";

    @TempDir
    Path tempDir;

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void readOnlyMountFlow() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "dav-content");
        Files.createDirectory(tempDir.resolve("sub"));

        String token = login();
        createStorage(token, "/wd", tempDir);
        setWebdavPassword(token, DAV_PASS);

        // 无凭据 → 401 + Digest challenge
        HttpResponse<String> challenge = send(req("PROPFIND", "/dav/").header("Depth", "0").build());
        assertThat(challenge.statusCode()).isEqualTo(401);
        assertThat(challenge.headers().firstValue("WWW-Authenticate").orElse("")).contains("Digest");

        // 根 PROPFIND Depth 1 → 列出挂载点 /wd
        HttpResponse<String> root = digest("PROPFIND", "/dav/", "1", null);
        assertThat(root.statusCode()).isEqualTo(207);
        assertThat(root.body()).contains("/dav/wd");

        // 挂载点 PROPFIND Depth 1 → 列出 hello.txt 与 sub 目录
        HttpResponse<String> listing = digest("PROPFIND", "/dav/wd", "1", null);
        assertThat(listing.statusCode()).isEqualTo(207);
        assertThat(listing.body()).contains("hello.txt").contains("sub");
        assertThat(listing.body()).contains("<D:collection/>");

        // GET 文件 → 内容
        HttpResponse<String> get = digest("GET", "/dav/wd/hello.txt", null, null);
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.body()).isEqualTo("dav-content");

        // GET 无凭据 → 401
        HttpResponse<String> noAuth = send(req("GET", "/dav/wd/hello.txt").build());
        assertThat(noAuth.statusCode()).isEqualTo(401);
    }

    // ─── Digest 客户端 ──────────────────────────────────────────

    /**
     * 执行一次带 Digest 的请求（先取 challenge，再带 Authorization 重试）。
     */
    private HttpResponse<String> digest(String method, String path, String depth, String body) throws Exception {
        HttpResponse<String> first = send(reqWithDepth(method, path, depth).build());
        if (first.statusCode() != 401) {
            return first;
        }
        Map<String, String> ch = parse(first.headers().firstValue("WWW-Authenticate").orElseThrow().substring(7));
        String authHeader = buildAuth(method, path, ch);
        return send(reqWithDepth(method, path, depth).header("Authorization", authHeader).build());
    }

    private String buildAuth(String method, String uri, Map<String, String> ch) {
        String realm = ch.get("realm");
        String nonce = ch.get("nonce");
        String qop = ch.get("qop");
        String ha1 = md5(USER + ":" + realm + ":" + DAV_PASS);
        String ha2 = md5(method + ":" + uri);
        String nc = "00000001";
        String cnonce = "abc123";
        String response = qop != null
                ? md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + "auth" + ":" + ha2)
                : md5(ha1 + ":" + nonce + ":" + ha2);
        StringBuilder sb = new StringBuilder("Digest ");
        sb.append("username=\"").append(USER).append("\", ");
        sb.append("realm=\"").append(realm).append("\", ");
        sb.append("nonce=\"").append(nonce).append("\", ");
        sb.append("uri=\"").append(uri).append("\", ");
        sb.append("response=\"").append(response).append("\"");
        if (qop != null) {
            sb.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
        }
        return sb.toString();
    }

    // ─── REST 准备步骤 ──────────────────────────────────────────

    private String login() throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + USER + "\",\"password\":\"" + PASS + "\"}"))
                .build());
        return objectMapper.readTree(resp.body()).path("data").path("accessToken").asText();
    }

    private void createStorage(String token, String mountPath, Path rootPath) throws Exception {
        String body = "{\"mountPath\":\"" + mountPath + "\",\"driver\":\"Local\",\"addition\":{\"rootPath\":\""
                + rootPath.toString().replace("\\", "\\\\") + "\"},\"disabled\":false}";
        HttpResponse<String> resp = send(HttpRequest.newBuilder(uri("/api/admin/storage/create"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    private void setWebdavPassword(String token, String password) throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(uri("/api/me/webdav-password"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"password\":\"" + password + "\"}"))
                .build());
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    // ─── HTTP 工具 ──────────────────────────────────────────────

    private HttpRequest.Builder req(String method, String path) {
        return HttpRequest.newBuilder(uri(path)).method(method, HttpRequest.BodyPublishers.noBody());
    }

    private HttpRequest.Builder reqWithDepth(String method, String path, String depth) {
        HttpRequest.Builder b = req(method, path);
        if (depth != null) {
            b.header("Depth", depth);
        }
        return b;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static final Pattern PARAM = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))");

    private Map<String, String> parse(String header) {
        Map<String, String> result = new HashMap<>();
        Matcher m = PARAM.matcher(header);
        while (m.find()) {
            result.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        }
        return result;
    }

    private static String md5(String input) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
