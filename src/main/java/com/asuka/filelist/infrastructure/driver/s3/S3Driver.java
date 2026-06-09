package com.asuka.filelist.infrastructure.driver.s3;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AWS S3（及 S3 兼容服务）驱动：读 + 写，下载经预签名 URL 由控制器 302 重定向。
 *
 * <p>目录以 {@code "/"} 结尾的 key 模拟；list 用 delimiter 切分目录/文件。
 */
public class S3Driver implements StorageDriver, DriverGetter, DriverRootProvider, DriverWriter {

    private static final DriverConfig CONFIG = new DriverConfig("S3", false, false, false, false, false, "/", true);

    private final ObjectMapper objectMapper;

    private Storage storage;
    private S3DriverAddition addition;
    private String rootFolder;

    private S3Client client;
    private S3Presigner presigner;

    public S3Driver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        this.rootFolder = addition.normalizedRootFolder();
    }

    @Override
    public Object addition() {
        return addition;
    }

    /**
     * 构建 S3 客户端/预签名器并验证桶连通性。
     */
    @Override
    public void init(DriverContext context) {
        if (addition.bucket() == null || addition.bucket().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "S3 bucket is required");
        }
        if (addition.accessKeyId() == null || addition.accessKeyId().isBlank()
                || addition.secretAccessKey() == null || addition.secretAccessKey().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "S3 credentials are required");
        }
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(addition.accessKeyId(), addition.secretAccessKey()));
        Region region = Region.of(addition.effectiveRegion());
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(addition.pathStyle())
                .build();

        var clientBuilder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        var presignerBuilder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        if (addition.endpoint() != null && !addition.endpoint().isBlank()) {
            URI endpoint = parseEndpoint(addition.endpoint());
            clientBuilder.endpointOverride(endpoint);
            presignerBuilder.endpointOverride(endpoint);
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(addition.bucket()).build());
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "S3 bucket is not accessible: " + summarize(ex));
        }
    }

    @Override
    public void drop(DriverContext context) {
        closeQuietly();
        this.client = null;
        this.presigner = null;
    }

    /**
     * 列目录：CommonPrefixes 为子目录，Contents 为文件（去除目录占位与前缀自身）。
     */
    @Override
    public List<FileObject> list(DriverContext context, FileObject dir, ListArgs args) {
        String prefix = toDirPrefix(dir.path());
        List<FileObject> result = new ArrayList<>();
        String continuation = null;
        try {
            do {
                ListObjectsV2Response resp = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(addition.bucket())
                        .prefix(prefix)
                        .delimiter("/")
                        .continuationToken(continuation)
                        .build());
                for (CommonPrefix cp : resp.commonPrefixes()) {
                    result.add(directoryObject(toActualPath(cp.prefix())));
                }
                for (S3Object obj : resp.contents()) {
                    if (obj.key().equals(prefix) || obj.key().endsWith("/")) {
                        continue; // 跳过目录占位对象
                    }
                    result.add(fileObject(toActualPath(obj.key()), obj.size(),
                            obj.lastModified()));
                }
                continuation = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
            } while (continuation != null);
        } catch (S3Exception ex) {
            throw remote("list", ex);
        }
        return result;
    }

    /**
     * 生成预签名下载 URL；非 file 协议由 DownloadController 直接 302。
     */
    @Override
    public FileLink link(DriverContext context, FileObject file, LinkArgs args) {
        String key = toKey(file.path());
        Duration ttl = Duration.ofSeconds(addition.effectiveSignExpireSec());
        try {
            GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(b -> b.bucket(addition.bucket()).key(key))
                    .build();
            URI url = presigner.presignGetObject(presign).url().toURI();
            return new FileLink(url, Map.of(), ttl, false, null, null);
        } catch (S3Exception | URISyntaxException ex) {
            throw remote("link", ex);
        }
    }

    /**
     * 查询 actualPath 对象：先按文件 headObject，404 时探测目录前缀。
     */
    @Override
    public FileObject get(DriverContext context, String actualPath) {
        String clean = PathUtils.fixAndCleanPath(actualPath);
        if ("/".equals(clean)) {
            return getRoot(context);
        }
        String key = toKey(clean);
        try {
            HeadObjectResponse head = client.headObject(HeadObjectRequest.builder()
                    .bucket(addition.bucket()).key(key).build());
            return fileObject(clean, head.contentLength(), head.lastModified());
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw remote("get", ex);
            }
        }
        // 文件不存在，探测是否为目录
        if (directoryExists(key + "/")) {
            return directoryObject(clean);
        }
        throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND, "S3 object does not exist");
    }

    @Override
    public FileObject getRoot(DriverContext context) {
        return directoryObject("/");
    }

    @Override
    public FileObject mkdir(DriverContext context, String parentPath, String dirName) {
        validateName(dirName);
        String actual = joinChild(parentPath, dirName);
        String key = toKey(actual) + "/";
        try {
            client.putObject(PutObjectRequest.builder().bucket(addition.bucket()).key(key).build(),
                    RequestBody.empty());
        } catch (S3Exception ex) {
            throw remote("mkdir", ex);
        }
        return directoryObject(actual);
    }

    @Override
    public FileObject put(DriverContext context, String parentPath, UploadFile file) {
        validateName(file.name());
        String actual = joinChild(parentPath, file.name());
        String key = toKey(actual);
        try {
            RequestBody body = file.size() >= 0
                    ? RequestBody.fromInputStream(file.inputStream(), file.size())
                    : RequestBody.fromBytes(file.inputStream().readAllBytes());
            PutObjectRequest.Builder req = PutObjectRequest.builder().bucket(addition.bucket()).key(key);
            if (file.mimeType() != null && !file.mimeType().isBlank()) {
                req.contentType(file.mimeType());
            }
            client.putObject(req.build(), body);
        } catch (S3Exception ex) {
            throw remote("put", ex);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read upload stream");
        }
        return get(context, actual);
    }

    @Override
    public void remove(DriverContext context, String path) {
        String key = toKey(path);
        List<ObjectIdentifier> toDelete = new ArrayList<>();
        toDelete.add(ObjectIdentifier.builder().key(key).build());
        toDelete.add(ObjectIdentifier.builder().key(key + "/").build());
        collectKeys(key + "/").forEach(k -> toDelete.add(ObjectIdentifier.builder().key(k).build()));
        deleteBatch(toDelete);
    }

    @Override
    public void rename(DriverContext context, String srcPath, String newName) {
        validateName(newName);
        String parent = parentOf(PathUtils.fixAndCleanPath(srcPath));
        String dst = joinChild(parent, newName);
        copyTree(srcPath, dst);
        remove(context, srcPath);
    }

    @Override
    public void move(DriverContext context, String srcPath, String dstDirPath) {
        String dst = joinChild(dstDirPath, baseName(srcPath));
        copyTree(srcPath, dst);
        remove(context, srcPath);
    }

    @Override
    public void copy(DriverContext context, String srcPath, String dstDirPath) {
        String dst = joinChild(dstDirPath, baseName(srcPath));
        copyTree(srcPath, dst);
    }

    // ─── key ↔ actualPath 映射（纯逻辑，单测覆盖）─────────────────

    /**
     * actualPath → 文件 key（无尾斜杠），拼接 rootFolder。
     */
    String toKey(String actualPath) {
        String clean = PathUtils.fixAndCleanPath(actualPath);
        String rel = "/".equals(clean) ? "" : clean.substring(1);
        if (rootFolder.isEmpty()) {
            return rel;
        }
        return rel.isEmpty() ? rootFolder : rootFolder + "/" + rel;
    }

    /**
     * actualPath → 列目录前缀（非空时带尾斜杠）。
     */
    String toDirPrefix(String actualPath) {
        String key = toKey(actualPath);
        return key.isEmpty() ? "" : key + "/";
    }

    /**
     * key/commonPrefix → actualPath（去 rootFolder 前缀、去尾斜杠、补首斜杠）。
     */
    String toActualPath(String key) {
        String stripped = key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
        if (!rootFolder.isEmpty()) {
            if (stripped.equals(rootFolder)) {
                return "/";
            }
            if (stripped.startsWith(rootFolder + "/")) {
                stripped = stripped.substring(rootFolder.length() + 1);
            }
        }
        return stripped.isEmpty() ? "/" : "/" + stripped;
    }

    // ─── 内部辅助 ───────────────────────────────────────────────

    /**
     * 判断指定前缀下是否存在对象（即视为目录）。
     */
    private boolean directoryExists(String prefix) {
        try {
            ListObjectsV2Response resp = client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(addition.bucket()).prefix(prefix).maxKeys(1).build());
            return resp.hasContents() && !resp.contents().isEmpty();
        } catch (S3Exception ex) {
            throw remote("get", ex);
        }
    }

    /**
     * 列出指定前缀下全部对象 key（用于目录删除/递归复制）。
     */
    private List<String> collectKeys(String prefix) {
        List<String> keys = new ArrayList<>();
        String continuation = null;
        try {
            do {
                ListObjectsV2Response resp = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(addition.bucket()).prefix(prefix).continuationToken(continuation).build());
                resp.contents().forEach(o -> keys.add(o.key()));
                continuation = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
            } while (continuation != null);
        } catch (S3Exception ex) {
            throw remote("list", ex);
        }
        return keys;
    }

    /**
     * 批量删除对象（每批 ≤1000）。
     */
    private void deleteBatch(List<ObjectIdentifier> ids) {
        if (ids.isEmpty()) {
            return;
        }
        try {
            for (int i = 0; i < ids.size(); i += 1000) {
                List<ObjectIdentifier> chunk = ids.subList(i, Math.min(i + 1000, ids.size()));
                client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(addition.bucket())
                        .delete(Delete.builder().objects(chunk).build())
                        .build());
            }
        } catch (S3Exception ex) {
            throw remote("remove", ex);
        }
    }

    /**
     * 将 src（文件或目录子树）复制到 dst actualPath。
     */
    private void copyTree(String srcPath, String dstPath) {
        String srcKey = toKey(srcPath);
        String dstKey = toKey(dstPath);
        // 文件：单对象复制
        try {
            client.headObject(HeadObjectRequest.builder().bucket(addition.bucket()).key(srcKey).build());
            copyObject(srcKey, dstKey);
            return;
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw remote("copy", ex);
            }
        }
        // 目录：递归复制子树
        String srcPrefix = srcKey + "/";
        List<String> keys = collectKeys(srcPrefix);
        if (keys.isEmpty()) {
            throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND, "S3 object does not exist");
        }
        for (String key : keys) {
            String rel = key.substring(srcPrefix.length());
            copyObject(key, dstKey + "/" + rel);
        }
    }

    /**
     * 复制单个对象。
     */
    private void copyObject(String srcKey, String dstKey) {
        try {
            client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(addition.bucket()).sourceKey(srcKey)
                    .destinationBucket(addition.bucket()).destinationKey(dstKey)
                    .build());
        } catch (S3Exception ex) {
            throw remote("copy", ex);
        }
    }

    private FileObject fileObject(String actualPath, Long size, Instant modified) {
        Instant ts = modified == null ? Instant.EPOCH : modified;
        return new BasicFileObject(actualPath, actualPath, baseName(actualPath),
                size == null ? 0L : size, ts, ts, false, Map.of());
    }

    private FileObject directoryObject(String actualPath) {
        String name = "/".equals(actualPath) ? "/" : baseName(actualPath);
        return new BasicFileObject(actualPath, actualPath, name, 0L, Instant.EPOCH, Instant.EPOCH, true, Map.of());
    }

    private String baseName(String actualPath) {
        String clean = PathUtils.fixAndCleanPath(actualPath);
        if ("/".equals(clean)) {
            return "/";
        }
        int idx = clean.lastIndexOf('/');
        return clean.substring(idx + 1);
    }

    private String parentOf(String cleanPath) {
        int idx = cleanPath.lastIndexOf('/');
        return idx <= 0 ? "/" : cleanPath.substring(0, idx);
    }

    private String joinChild(String parentPath, String name) {
        String parent = PathUtils.fixAndCleanPath(parentPath);
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()
                || name.contains("/") || name.contains("\\")
                || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid object name");
        }
    }

    private S3DriverAddition parseAddition(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "S3 driver addition is required");
        }
        try {
            return objectMapper.readValue(json, S3DriverAddition.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid S3 driver addition");
        }
    }

    private URI parseEndpoint(String endpoint) {
        try {
            String normalized = endpoint.contains("://") ? endpoint : "https://" + endpoint;
            return new URI(normalized);
        } catch (URISyntaxException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid S3 endpoint");
        }
    }

    private BusinessException remote(String op, Exception ex) {
        return new BusinessException(ErrorCode.DRIVER_REMOTE_ERROR, "S3 " + op + " failed: " + summarize(ex));
    }

    private String summarize(Exception ex) {
        String msg = ex.getMessage();
        return msg == null ? ex.getClass().getSimpleName() : msg;
    }

    private void closeQuietly() {
        try {
            if (client != null) {
                client.close();
            }
            if (presigner != null) {
                presigner.close();
            }
        } catch (RuntimeException ignored) {
            // 关闭失败不影响 drop
        }
    }
}
