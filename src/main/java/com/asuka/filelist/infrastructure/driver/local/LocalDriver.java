package com.asuka.filelist.infrastructure.driver.local;

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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 本地文件系统只读驱动。
 */
public class LocalDriver implements StorageDriver, DriverGetter, DriverRootProvider {

    private static final DriverConfig CONFIG = new DriverConfig("Local", true, true, false, false, false, "/", true);

    private final ObjectMapper objectMapper;
    private final List<String> rootWhitelist;

    private Storage storage;
    private LocalDriverAddition addition;
    private Path rootPath;
    private Path realRootPath;

    public LocalDriver(ObjectMapper objectMapper, List<String> rootWhitelist) {
        this.objectMapper = objectMapper;
        this.rootWhitelist = rootWhitelist == null ? List.of() : rootWhitelist;
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
    }

    @Override
    public Object addition() {
        return addition;
    }

    /**
     * 校验 rootPath 并准备真实根路径。
     */
    @Override
    public void init(DriverContext context) {
        this.rootPath = validateRootPath(addition.rootPath());
        this.realRootPath = toRealPath(rootPath);
        ensureWhitelisted(realRootPath);
    }

    @Override
    public void drop(DriverContext context) {
        this.rootPath = null;
        this.realRootPath = null;
    }

    /**
     * 获取本地目录下的子项。
     */
    @Override
    public List<FileObject> list(DriverContext context, FileObject dir, ListArgs args) {
        Path dirPath = resolveExistingPath(dir.path());
        if (!Files.isDirectory(dirPath)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Target is not a directory");
        }
        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream.map(this::toFileObject).toList();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to list local directory");
        }
    }

    /**
     * 生成本地文件 URI 链接。
     */
    @Override
    public FileLink link(DriverContext context, FileObject file, LinkArgs args) {
        Path path = resolveExistingPath(file.path());
        if (Files.isDirectory(path)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Directory cannot be linked");
        }
        URI uri = path.toUri();
        return new FileLink(uri, Map.of(), Duration.ofMinutes(10), false, null, null);
    }

    /**
     * 查询指定 actualPath 的文件对象。
     */
    @Override
    public FileObject get(DriverContext context, String actualPath) {
        return toFileObject(resolveExistingPath(actualPath));
    }

    /**
     * 返回驱动根目录对象。
     */
    @Override
    public FileObject getRoot(DriverContext context) {
        return toFileObject(rootPath);
    }

    /**
     * 解析 addition JSON。
     */
    private LocalDriverAddition parseAddition(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Local driver addition is required");
        }
        try {
            return objectMapper.readValue(json, LocalDriverAddition.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid Local driver addition");
        }
    }

    /**
     * 校验 rootPath 是存在的绝对目录。
     */
    private Path validateRootPath(String rawRootPath) {
        if (rawRootPath == null || rawRootPath.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Local rootPath cannot be empty");
        }
        // 先检查是否绝对路径，防止 toAbsolutePath() 将相对路径解析为基于 cwd 的路径（review P2 fix）
        Path raw = Path.of(rawRootPath);
        if (!raw.isAbsolute()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Local rootPath must be an absolute path");
        }
        Path path = raw.toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Local rootPath must be an existing absolute directory");
        }
        return path;
    }

    /**
     * 校验根目录在白名单目录下。
     */
    private void ensureWhitelisted(Path realPath) {
        boolean allowed = rootWhitelist.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(this::toWhitelistPath)
                .anyMatch(realPath::startsWith);
        if (!allowed) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Local rootPath is not whitelisted");
        }
    }

    /**
     * 白名单可作为尚未创建的前缀；若存在则解析真实路径以兼容符号链接。
     */
    private Path toWhitelistPath(String rawPath) {
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        return Files.exists(path) ? toRealPath(path) : path;
    }

    /**
     * 将 actualPath 映射到真实本地路径，并防止越过 rootPath。
     */
    private Path resolveExistingPath(String actualPath) {
        String cleanPath = PathUtils.fixAndCleanPath(actualPath);
        Path candidate = "/".equals(cleanPath)
                ? rootPath
                : rootPath.resolve(cleanPath.substring(1)).normalize();
        if (!Files.exists(candidate)) {
            throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND, "Local object does not exist");
        }
        Path realPath = toRealPath(candidate);
        if (!realPath.startsWith(realRootPath)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Local path escapes rootPath");
        }
        return realPath;
    }

    /**
     * 转换为真实路径，统一处理 I/O 异常。
     */
    private Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Local path cannot be resolved");
        }
    }

    /**
     * 将本地 Path 转换为 FileObject。
     */
    private FileObject toFileObject(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            String actualPath = toActualPath(path);
            return new BasicFileObject(
                    actualPath,
                    actualPath,
                    objectName(path, actualPath),
                    attrs.isDirectory() ? 0L : attrs.size(),
                    attrs.lastModifiedTime().toInstant(),
                    createdAt(attrs),
                    attrs.isDirectory(),
                    Map.of()
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read local object attributes");
        }
    }

    /**
     * 将本地路径转换为驱动内 actualPath。
     */
    private String toActualPath(Path path) {
        Path relative = realRootPath.relativize(path);
        if (relative.toString().isBlank()) {
            return "/";
        }
        return PathUtils.fixAndCleanPath(relative.toString());
    }

    /**
     * 根目录名称固定为 /，普通文件取文件名。
     */
    private String objectName(Path path, String actualPath) {
        if ("/".equals(actualPath)) {
            return "/";
        }
        return path.getFileName().toString();
    }

    /**
     * 获取创建时间，缺失时回退到最后修改时间。
     */
    private Instant createdAt(BasicFileAttributes attrs) {
        Instant created = attrs.creationTime().toInstant();
        return created == null ? attrs.lastModifiedTime().toInstant() : created;
    }
}
