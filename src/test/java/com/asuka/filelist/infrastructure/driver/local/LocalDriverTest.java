package com.asuka.filelist.infrastructure.driver.local;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.domain.fs.FileLink;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import com.asuka.filelist.infrastructure.driver.UploadFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LocalDriver 文件读取和路径安全测试。
 */
class LocalDriverTest {

    @TempDir
    private Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * LocalDriver 能读取根目录、获取文件对象并生成 file URI。
     */
    @Test
    void listGetAndLink_returnsLocalObjects() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        LocalDriver driver = initializedDriver(List.of(tempDir.toString()));

        FileObject root = driver.get(context(), "/");
        List<FileObject> children = driver.list(context(), root, new ListArgs("/", false));
        FileLink link = driver.link(context(), children.get(0), new LinkArgs("", Map.of(), "", false));

        assertThat(children).extracting(FileObject::name).containsExactly("a.txt");
        assertThat(link.url().getScheme()).isEqualTo("file");
    }

    /**
     * rootPath 不在白名单下时拒绝初始化。
     */
    @Test
    void initRejectsRootOutsideWhitelist() {
        LocalDriver driver = new LocalDriver(objectMapper, List.of(tempDir.resolve("allowed").toString()));
        driver.setStorage(storage(tempDir.toString()));

        assertThatThrownBy(() -> driver.init(context()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Local rootPath is not whitelisted");
    }

    /**
     * 访问不存在路径返回业务异常。
     */
    @Test
    void getMissingPath_throwsObjectNotFound() {
        LocalDriver driver = initializedDriver(List.of(tempDir.toString()));

        assertThatThrownBy(() -> driver.get(context(), "/missing.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Local object does not exist");
    }

    /**
     * mkdir 与 put 后，list 能反映新目录和文件。
     */
    @Test
    void mkdirAndPut_thenListReflectsChanges() throws Exception {
        LocalDriver driver = initializedDriver(List.of(tempDir.toString()));

        FileObject dir = driver.mkdir(context(), "/", "sub");
        driver.put(context(), "/sub",
                new UploadFile("b.txt", 5, "text/plain",
                        new ByteArrayInputStream("world".getBytes(StandardCharsets.UTF_8))));

        List<FileObject> children = driver.list(context(), dir, new ListArgs("/sub", false));
        assertThat(dir.directory()).isTrue();
        assertThat(children).extracting(FileObject::name).containsExactly("b.txt");
        assertThat(Files.readString(tempDir.resolve("sub/b.txt"))).isEqualTo("world");
    }

    /**
     * rename 修改文件名，move/copy 在同存储内迁移，remove 递归删除。
     */
    @Test
    void renameMoveCopyRemove_behaveCorrectly() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        Files.createDirectory(tempDir.resolve("dst"));
        Files.createDirectory(tempDir.resolve("moved"));
        LocalDriver driver = initializedDriver(List.of(tempDir.toString()));

        driver.rename(context(), "/a.txt", "renamed.txt");
        assertThat(Files.exists(tempDir.resolve("renamed.txt"))).isTrue();

        driver.copy(context(), "/renamed.txt", "/dst");
        assertThat(Files.exists(tempDir.resolve("dst/renamed.txt"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("renamed.txt"))).isTrue();

        driver.move(context(), "/renamed.txt", "/moved");
        assertThat(Files.exists(tempDir.resolve("renamed.txt"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("moved/renamed.txt"))).isTrue();

        driver.remove(context(), "/dst");
        assertThat(Files.exists(tempDir.resolve("dst"))).isFalse();
    }

    /**
     * 上传文件名包含路径分隔符时拒绝，防止越界写入。
     */
    @Test
    void put_rejectsNameWithSeparator() {
        LocalDriver driver = initializedDriver(List.of(tempDir.toString()));

        assertThatThrownBy(() -> driver.put(context(), "/",
                new UploadFile("../evil.txt", 1, "text/plain",
                        new ByteArrayInputStream(new byte[]{1}))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid object name");
    }

    /**
     * 移动到已存在的目标时拒绝覆盖。
     */
    @Test
    void move_rejectsExistingTarget() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "1");
        Files.createDirectory(tempDir.resolve("dst"));
        Files.writeString(tempDir.resolve("dst/a.txt"), "2");
        LocalDriver driver = initializedDriver(List.of(tempDir.toString()));

        assertThatThrownBy(() -> driver.move(context(), "/a.txt", "/dst"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Target already exists");
    }

    /**
     * 创建已初始化驱动。
     */
    private LocalDriver initializedDriver(List<String> whitelist) {
        LocalDriver driver = new LocalDriver(objectMapper, whitelist);
        driver.setStorage(storage(tempDir.toString()));
        driver.init(context());
        return driver;
    }

    /**
     * 构造测试用 storage。
     */
    private Storage storage(String rootPath) {
        return new Storage(1L, "/local", 0, "Local", 30, "work",
                "{\"rootPath\":\"" + rootPath + "\"}", "", null,
                false, false, false, "name", "asc", "front", false, "proxy", false);
    }

    /**
     * 构造驱动上下文。
     */
    private DriverContext context() {
        return new DriverContext("/local", Map.of());
    }
}
