package com.asuka.filelist.infrastructure.driver.baidu;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.fs.BasicFileObject;
import com.asuka.filelist.domain.fs.FileLink;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.DriverConfig;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.DriverGetter;
import com.asuka.filelist.infrastructure.driver.DriverRootProvider;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import com.asuka.filelist.infrastructure.driver.StorageDriver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 百度网盘只读驱动：list/get/link。下载须服务端带 {@code User-Agent: pan.baidu.com} 代理拉取。
 *
 * <p>不实现 {@link com.asuka.filelist.infrastructure.driver.DriverWriter}，写操作由应用层映射为
 * DRIVER_NOT_SUPPORTED（百度写为分片上传，本轮不做，见设计 §7）。
 */
public class BaiduDriver implements StorageDriver, DriverGetter, DriverRootProvider {

    /** 百度下载直链要求该 UA，否则 403/重定向丢失。 */
    static final String BAIDU_USER_AGENT = "pan.baidu.com";

    private static final DriverConfig CONFIG = new DriverConfig("BaiduNetdisk", false, false, true, false, true, "/", true);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private Storage storage;
    private BaiduDriverAddition addition;
    private String rootPath;
    private BaiduTokenManager tokenManager;

    // 端点可在测试中覆盖
    private String oauthBase = "https://openapi.baidu.com";
    private String panBase = "https://pan.baidu.com";

    public BaiduDriver(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public DriverConfig config() {
        return CONFIG;
    }

    @Override
    public Storage storage() {
        return storage;
    }

    @Override
    public void setStorage(Storage storage) {
        this.storage = storage;
        this.addition = parseAddition(storage.addition());
        this.rootPath = addition.normalizedRootPath();
    }

    @Override
    public Object addition() {
        return addition;
    }

    /**
     * 解析配置并预取一次 token 验证可用。
     */
    @Override
    public void init(DriverContext context) {
        if (addition.refreshToken() == null || addition.refreshToken().isBlank()
                || addition.clientId() == null || addition.clientId().isBlank()
                || addition.clientSecret() == null || addition.clientSecret().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Baidu refreshToken/clientId/clientSecret are required");
        }
        this.tokenManager = new BaiduTokenManager(httpClient, objectMapper, addition, oauthBase);
        this.tokenManager.getAccessToken();
    }

    @Override
    public void drop(DriverContext context) {
        this.tokenManager = null;
    }

    /**
     * 列目录：xpan/file?method=list。
     */
    @Override
    public List<FileObject> list(DriverContext context, FileObject dir, ListArgs args) {
        String baiduDir = toBaiduPath(dir.path());
        String token = tokenManager.getAccessToken();
        String url = panBase + "/rest/2.0/xpan/file"
                + "?method=list&dir=" + encode(baiduDir)
                + "&access_token=" + encode(token);
        JsonNode root = call("list", url);
        List<FileObject> result = new ArrayList<>();
        for (JsonNode entry : root.path("list")) {
            result.add(toFileObject(entry));
        }
        return result;
    }

    /**
     * 取下载直链并注入百度 UA 头，由 DownloadController 代理流式拉取。
     */
    @Override
    public FileLink link(DriverContext context, FileObject file, LinkArgs args) {
        String fsid = file.id();
        if (fsid == null || fsid.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Baidu file id (fs_id) is required for link");
        }
        String token = tokenManager.getAccessToken();
        String url = panBase + "/rest/2.0/xpan/multimedia"
                + "?method=filemetas&fsids=[" + encode(fsid) + "]&dlink=1"
                + "&access_token=" + encode(token);
        JsonNode root = call("link", url);
        JsonNode list = root.path("list");
        if (!list.isArray() || list.isEmpty() || list.get(0).path("dlink").asText("").isBlank()) {
            throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Baidu dlink not available");
        }
        String dlink = list.get(0).path("dlink").asText() + "&access_token=" + encode(token);
        return new FileLink(URI.create(dlink), Map.of("User-Agent", BAIDU_USER_AGENT),
                Duration.ofHours(8), false, null, null);
    }

    /**
     * 查询 actualPath：根返回目录对象，否则列父目录匹配名。
     */
    @Override
    public FileObject get(DriverContext context, String actualPath) {
        String clean = PathUtils.fixAndCleanPath(actualPath);
        if ("/".equals(clean)) {
            return getRoot(context);
        }
        String parent = parentOf(clean);
        String name = clean.substring(clean.lastIndexOf('/') + 1);
        for (FileObject child : list(context, directoryObject(parent), new ListArgs(parent, false))) {
            if (child.name().equals(name)) {
                return child;
            }
        }
        throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND, "Baidu object does not exist");
    }

    @Override
    public FileObject getRoot(DriverContext context) {
        return directoryObject("/");
    }

    // ─── 路径映射（纯逻辑，单测覆盖）────────────────────────────

    /**
     * actualPath → 百度绝对路径（拼 rootPath）。
     */
    String toBaiduPath(String actualPath) {
        String clean = PathUtils.fixAndCleanPath(actualPath);
        if ("/".equals(clean)) {
            return rootPath;
        }
        return "/".equals(rootPath) ? clean : rootPath + clean;
    }

    /**
     * 百度绝对路径 → actualPath（去 rootPath 前缀）。
     */
    String toActualPath(String baiduPath) {
        String clean = PathUtils.fixAndCleanPath(baiduPath);
        if ("/".equals(rootPath)) {
            return clean;
        }
        if (clean.equals(rootPath)) {
            return "/";
        }
        if (clean.startsWith(rootPath + "/")) {
            return clean.substring(rootPath.length());
        }
        return clean;
    }

    // ─── 内部辅助 ───────────────────────────────────────────────

    /**
     * 将百度 list 条目映射为 FileObject，fs_id 存入 id 供 link 使用。
     */
    private FileObject toFileObject(JsonNode entry) {
        boolean isDir = entry.path("isdir").asInt(0) == 1;
        String actualPath = toActualPath(entry.path("path").asText());
        String name = entry.path("server_filename").asText();
        long size = entry.path("size").asLong(0);
        long mtime = entry.path("server_mtime").asLong(0);
        Instant ts = Instant.ofEpochSecond(mtime);
        String fsId = entry.path("fs_id").asText("");
        return new BasicFileObject(fsId, actualPath, name, isDir ? 0L : size, ts, ts, isDir, Map.of());
    }

    private FileObject directoryObject(String actualPath) {
        String name = "/".equals(actualPath) ? "/" : actualPath.substring(actualPath.lastIndexOf('/') + 1);
        return new BasicFileObject("", actualPath, name, 0L, Instant.EPOCH, Instant.EPOCH, true, Map.of());
    }

    private String parentOf(String cleanPath) {
        int idx = cleanPath.lastIndexOf('/');
        return idx <= 0 ? "/" : cleanPath.substring(0, idx);
    }

    /**
     * 调百度 REST 接口，校验 errno 并返回 JSON 根。
     */
    private JsonNode call(String op, String url) {
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR,
                        "Baidu " + op + " failed with status " + resp.statusCode());
            }
            JsonNode root = objectMapper.readTree(resp.body());
            int errno = root.path("errno").asInt(0);
            if (errno != 0) {
                throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Baidu " + op + " errno=" + errno);
            }
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Baidu " + op + " failed: " + ex.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private BaiduDriverAddition parseAddition(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Baidu driver addition is required");
        }
        try {
            return objectMapper.readValue(json, BaiduDriverAddition.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid Baidu driver addition");
        }
    }

    /**
     * 测试用：覆盖 OAuth/Pan 端点基址。
     */
    void setEndpointsForTest(String oauthBase, String panBase) {
        this.oauthBase = oauthBase;
        this.panBase = panBase;
    }
}
