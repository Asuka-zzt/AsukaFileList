package com.asuka.filelist.infrastructure.driver.local;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.domain.fs.FileLink;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
