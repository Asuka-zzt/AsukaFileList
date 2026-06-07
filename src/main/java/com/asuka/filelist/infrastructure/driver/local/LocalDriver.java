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
import com.asuka.filelist.infrastructure.driver.DriverWriter;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import com.asuka.filelist.infrastructure.driver.StorageDriver;
import com.asuka.filelist.infrastructure.driver.UploadFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 本地文件系统只读驱动。
 */
public class LocalDriver implements StorageDriver, DriverGetter, DriverRootProvider, DriverWriter {

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
     * 在 parentPath 下新建子目录。
     */
    @Override
    public FileObject mkdir(DriverContext context, String parentPath, String dirName) {
        validateName(dirName);
        resolveExistingDirectory(parentPath);
        Path target = resolveWritablePath(joinChild(parentPath, dirName));
        try {
            Files.createDirectory(target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to create local directory");
        }
        return toFileObject(toRealPath(target));
    }

    /**
     * 将 srcPath 移动到 dstDirPath 目录下。
     */
    @Override
    public void move(DriverContext context, String srcPath, String dstDirPath) {
        Path src = resolveExistingNonRoot(srcPath);
        Path dstDir = resolveExistingDirectory(dstDirPath);
        moveTo(src, dstDir.resolve(src.getFileName()));
    }

    /**
     * 将 srcPath 复制到 dstDirPath 目录下。
     */
    @Override
    public void copy(DriverContext context, String srcPath, String dstDirPath) {
        Path src = resolveExistingNonRoot(srcPath);
        Path dstDir = resolveExistingDirectory(dstDirPath);
        Path target = dstDir.resolve(src.getFileName());
        if (Files.exists(target)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Target already exists");
        }
        copyRecursively(src, target);
    }

    /**
     * 在同目录内将 srcPath 重命名为 newName。
     */
    @Override
    public void rename(DriverContext context, String srcPath, String newName) {
        validateName(newName);
        Path src = resolveExistingNonRoot(srcPath);
        moveTo(src, src.resolveSibling(newName));
    }

    /**
     * 删除文件或目录（目录递归删除）。
     */
    @Override
    public void remove(DriverContext context, String path) {
        Path target = resolveExistingNonRoot(path);
        deleteRecursively(target);
    }

    /**
     * 将上传流写入 parentPath 目录。
     */
    @Override
    public FileObject put(DriverContext context, String parentPath, UploadFile file) {
        validateName(file.name());
        resolveExistingDirectory(parentPath);
        Path target = resolveWritablePath(joinChild(parentPath, file.name()));
        try {
            Files.copy(file.inputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to write local file");
        }
        return toFileObject(toRealPath(target));
    }

    /**
     * 解析必须已存在且非根目录的对象路径。
     */
    private Path resolveExistingNonRoot(String actualPath) {
        Path path = resolveExistingPath(actualPath);
        if (path.equals(realRootPath)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Operation on root path is not allowed");
        }
        return path;
    }

    /**
     * 解析必须已存在的目录路径。
     */
    private Path resolveExistingDirectory(String actualPath) {
        Path path = resolveExistingPath(actualPath);
        if (!Files.isDirectory(path)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Target is not a directory");
        }
        return path;
    }

    /**
     * 解析允许尚未存在的写入目标，校验不越过 rootPath（含符号链接逃逸）。
     */
    private Path resolveWritablePath(String actualPath) {
        String cleanPath = PathUtils.fixAndCleanPath(actualPath);
        if ("/".equals(cleanPath)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot write to root path");
        }
        Path candidate = rootPath.resolve(cleanPath.substring(1)).normalize();
        if (Files.exists(candidate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Target already exists");
        }
        Path ancestor = candidate.getParent();
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null || !toRealPath(ancestor).startsWith(realRootPath)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Local path escapes rootPath");
        }
        return candidate;
    }

    /**
     * 校验文件/目录名合法，禁止路径分隔符与特殊目录名。
     */
    private void validateName(String name) {
        if (name == null || name.isBlank()
                || name.contains("/") || name.contains("\\")
                || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid object name");
        }
    }

    /**
     * 拼接父目录 actualPath 与子项名。
     */
    private String joinChild(String parentPath, String name) {
        String parent = PathUtils.fixAndCleanPath(parentPath);
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    /**
     * 执行移动/重命名，目标已存在时拒绝。
     */
    private void moveTo(Path src, Path target) {
        if (Files.exists(target)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Target already exists");
        }
        try {
            Files.move(src, target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to move local object");
        }
    }

    /**
     * 递归复制文件或目录，保留属性。
     */
    private void copyRecursively(Path src, Path target) {
        try (Stream<Path> walk = Files.walk(src)) {
            walk.forEach(source -> {
                Path dest = target.resolve(src.relativize(source).toString());
                try {
                    Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (IOException | UncheckedIOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to copy local object");
        }
    }

    /**
     * 递归删除文件或目录，先删子项后删父项。
     */
    private void deleteRecursively(Path target) {
        try (Stream<Path> walk = Files.walk(target)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (IOException | UncheckedIOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to remove local object");
        }
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
