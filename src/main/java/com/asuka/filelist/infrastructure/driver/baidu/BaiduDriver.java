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
import com.asuka.filelist.infrastructure.driver.DriverWriter;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import com.asuka.filelist.infrastructure.driver.StorageDriver;
import com.asuka.filelist.infrastructure.driver.UploadFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 百度网盘读写驱动：list/get/link + mkdir/put/remove/rename/move/copy。
 *
 * <p>下载须服务端带 {@code User-Agent: pan.baidu.com} 代理拉取。上传走百度官方
 * precreate → superfile2（分片）→ create 流程；其余写操作走 {@code filemanager}（同步）。
 */
public class BaiduDriver implements StorageDriver, DriverGetter, DriverRootProvider, DriverWriter {

    /** 百度下载直链要求该 UA，否则 403/重定向丢失。 */
    static final String BAIDU_USER_AGENT = "pan.baidu.com";

    /** 普通用户分片大小 4MB。 */
    private static final int BLOCK_SIZE = 4 * 1024 * 1024;

    private static final DriverConfig CONFIG = new DriverConfig("BaiduNetdisk", false, false, true, false, false, "/", true);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private Storage storage;
    private BaiduDriverAddition addition;
    private String rootPath;
    private BaiduTokenManager tokenManager;

    // 端点可在测试中覆盖
    private String oauthBase = "https://openapi.baidu.com";
    private String panBase = "https://pan.baidu.com";
    private String uploadBase = "https://d.pcs.baidu.com";

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

    // ─── 写能力（DriverWriter）──────────────────────────────────

    /**
     * 新建目录：xpan/file?method=create&isdir=1。
     */
    @Override
    public FileObject mkdir(DriverContext context, String parentPath, String dirName) {
        validateName(dirName);
        String actual = joinChild(parentPath, dirName);
        String token = tokenManager.getAccessToken();
        String url = panBase + "/rest/2.0/xpan/file?method=create&access_token=" + encode(token);
        callPost("mkdir", url, "path=" + encode(toBaiduPath(actual)) + "&isdir=1&rtype=0");
        return directoryObject(actual);
    }

    /**
     * 上传：先 spool 到临时文件算分块 MD5，再 precreate → superfile2（逐块）→ create。
     */
    @Override
    public FileObject put(DriverContext context, String parentPath, UploadFile file) {
        validateName(file.name());
        String actual = joinChild(parentPath, file.name());
        String baiduPath = toBaiduPath(actual);
        String token = tokenManager.getAccessToken();
        Path temp = null;
        try {
            temp = Files.createTempFile("asuka-baidu-", ".upload");
            long size = Files.copy(file.inputStream(), temp, StandardCopyOption.REPLACE_EXISTING);
            List<String> blockMd5s = computeBlockMd5s(temp, size);
            String blockListJson = json(blockMd5s);

            JsonNode pre = callPost("precreate",
                    panBase + "/rest/2.0/xpan/file?method=precreate&access_token=" + encode(token),
                    "path=" + encode(baiduPath) + "&size=" + size + "&isdir=0&autoinit=1"
                            + "&rtype=3&block_list=" + encode(blockListJson));
            String uploadId = pre.path("uploadid").asText();
            if (uploadId.isBlank()) {
                throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Baidu precreate returned no uploadid");
            }
            for (int seq : blocksToUpload(pre, blockMd5s.size())) {
                uploadBlock(token, baiduPath, uploadId, seq, temp);
            }
            callPost("create",
                    panBase + "/rest/2.0/xpan/file?method=create&access_token=" + encode(token),
                    "path=" + encode(baiduPath) + "&size=" + size + "&isdir=0&rtype=3"
                            + "&uploadid=" + encode(uploadId) + "&block_list=" + encode(blockListJson));
            return get(context, actual);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Baidu upload spool failed: " + ex.getMessage());
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * 删除：filemanager opera=delete。
     */
    @Override
    public void remove(DriverContext context, String path) {
        String token = tokenManager.getAccessToken();
        filemanager("remove", "delete", token, json(List.of(toBaiduPath(path))));
    }

    /**
     * 同目录重命名：filemanager opera=rename。
     */
    @Override
    public void rename(DriverContext context, String srcPath, String newName) {
        validateName(newName);
        String token = tokenManager.getAccessToken();
        String item = json(List.of(Map.of("path", toBaiduPath(srcPath), "newname", newName)));
        filemanager("rename", "rename", token, item);
    }

    /**
     * 移动到目标目录（保留原名）：filemanager opera=move。
     */
    @Override
    public void move(DriverContext context, String srcPath, String dstDirPath) {
        String token = tokenManager.getAccessToken();
        String item = json(List.of(Map.of(
                "path", toBaiduPath(srcPath),
                "dest", toBaiduPath(dstDirPath),
                "newname", baseName(srcPath))));
        filemanager("move", "move", token, item);
    }

    /**
     * 复制到目标目录（保留原名）：filemanager opera=copy。
     */
    @Override
    public void copy(DriverContext context, String srcPath, String dstDirPath) {
        String token = tokenManager.getAccessToken();
        String item = json(List.of(Map.of(
                "path", toBaiduPath(srcPath),
                "dest", toBaiduPath(dstDirPath),
                "newname", baseName(srcPath))));
        filemanager("copy", "copy", token, item);
    }

    // ─── 写能力辅助 ─────────────────────────────────────────────

    /**
     * filemanager 同步操作（async=0）。
     */
    private void filemanager(String op, String opera, String token, String filelistJson) {
        String url = panBase + "/rest/2.0/xpan/file?method=filemanager&access_token=" + encode(token)
                + "&opera=" + opera;
        callPost(op, url, "async=0&filelist=" + encode(filelistJson));
    }

    /**
     * 计算每个 4MB 分块的十六进制 MD5；空文件返回单个空内容 MD5。
     */
    private List<String> computeBlockMd5s(Path file, long size) throws IOException {
        List<String> md5s = new ArrayList<>();
        if (size == 0) {
            md5s.add(md5(new byte[0]));
            return md5s;
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] block;
            while ((block = in.readNBytes(BLOCK_SIZE)).length > 0) {
                md5s.add(md5(block));
            }
        }
        return md5s;
    }

    /**
     * precreate 返回待传分块序号；为空则视为全部需要上传。
     */
    private List<Integer> blocksToUpload(JsonNode pre, int total) {
        List<Integer> seqs = new ArrayList<>();
        JsonNode blockList = pre.path("block_list");
        if (blockList.isArray() && !blockList.isEmpty()) {
            blockList.forEach(node -> seqs.add(node.asInt()));
        } else {
            for (int i = 0; i < total; i++) {
                seqs.add(i);
            }
        }
        return seqs;
    }

    /**
     * 上传单个分块到 superfile2（multipart）。
     */
    private void uploadBlock(String token, String baiduPath, String uploadId, int seq, Path temp) throws IOException {
        byte[] block;
        try (InputStream in = Files.newInputStream(temp)) {
            in.skipNBytes((long) seq * BLOCK_SIZE);
            block = in.readNBytes(BLOCK_SIZE);
        }
        String boundary = "----asuka" + System.nanoTime();
        String url = uploadBase + "/rest/2.0/pcs/superfile2?method=upload&access_token=" + encode(token)
                + "&type=tmpfile&path=" + encode(baiduPath) + "&uploadid=" + encode(uploadId) + "&partseq=" + seq;
        try {
            HttpResponse<String> resp = httpClient.send(HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .header("User-Agent", BAIDU_USER_AGENT)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, block)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR,
                        "Baidu superfile2 failed with status " + resp.statusCode());
            }
            JsonNode node = objectMapper.readTree(resp.body());
            if (node.path("error_code").asInt(0) != 0) {
                throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR,
                        "Baidu superfile2 error_code=" + node.path("error_code").asInt());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "Baidu superfile2 failed: " + ex.getMessage());
        }
    }

    /**
     * 构造单文件 multipart/form-data 请求体（字段名 file）。
     */
    private byte[] multipart(String boundary, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"blob\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * 十六进制 MD5。
     */
    private String md5(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MD5 unavailable");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to serialize Baidu request");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()
                || name.contains("/") || name.contains("\\")
                || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid object name");
        }
    }

    private String joinChild(String parentPath, String name) {
        String parent = PathUtils.fixAndCleanPath(parentPath);
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    private String baseName(String actualPath) {
        String clean = PathUtils.fixAndCleanPath(actualPath);
        if ("/".equals(clean)) {
            return "/";
        }
        return clean.substring(clean.lastIndexOf('/') + 1);
    }

    private void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 临时文件清理失败不影响结果
            }
        }
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

    /**
     * 调百度 REST 接口（POST 表单），校验 errno 并返回 JSON 根。
     */
    private JsonNode callPost(String op, String url, String form) {
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("User-Agent", BAIDU_USER_AGENT)
                            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                            .build(),
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
     * 测试用：覆盖 OAuth/Pan 端点基址（上传基址默认同 Pan）。
     */
    void setEndpointsForTest(String oauthBase, String panBase) {
        setEndpointsForTest(oauthBase, panBase, panBase);
    }

    /**
     * 测试用：覆盖 OAuth/Pan/上传 端点基址。
     */
    void setEndpointsForTest(String oauthBase, String panBase, String uploadBase) {
        this.oauthBase = oauthBase;
        this.panBase = panBase;
        this.uploadBase = uploadBase;
    }
}
