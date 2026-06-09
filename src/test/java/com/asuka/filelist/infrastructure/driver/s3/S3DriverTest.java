package com.asuka.filelist.infrastructure.driver.s3;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.domain.storage.Storage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3 驱动纯逻辑测试：key↔actualPath 映射、addition 解析（无需真实 S3）。
 */
class S3DriverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 空 rootFolder：key 即去掉首斜杠的相对路径，往返一致。
     */
    @Test
    void keyMapping_withEmptyRootFolder() {
        S3Driver driver = driver("{\"bucket\":\"b\",\"accessKeyId\":\"k\",\"secretAccessKey\":\"s\"}");

        assertThat(driver.toKey("/")).isEmpty();
        assertThat(driver.toKey("/a/b.txt")).isEqualTo("a/b.txt");
        assertThat(driver.toDirPrefix("/")).isEmpty();
        assertThat(driver.toDirPrefix("/a")).isEqualTo("a/");
        assertThat(driver.toActualPath("a/b.txt")).isEqualTo("/a/b.txt");
        assertThat(driver.toActualPath("a/")).isEqualTo("/a");
    }

    /**
     * 非空 rootFolder：key 带前缀，actualPath 去前缀且根映射回 "/"。
     */
    @Test
    void keyMapping_withRootFolder() {
        S3Driver driver = driver(
                "{\"bucket\":\"b\",\"accessKeyId\":\"k\",\"secretAccessKey\":\"s\",\"rootFolder\":\"/data/\"}");

        assertThat(driver.toKey("/")).isEqualTo("data");
        assertThat(driver.toKey("/a/b.txt")).isEqualTo("data/a/b.txt");
        assertThat(driver.toDirPrefix("/")).isEqualTo("data/");
        assertThat(driver.toDirPrefix("/a")).isEqualTo("data/a/");
        assertThat(driver.toActualPath("data")).isEqualTo("/");
        assertThat(driver.toActualPath("data/")).isEqualTo("/");
        assertThat(driver.toActualPath("data/a/b.txt")).isEqualTo("/a/b.txt");
        assertThat(driver.toActualPath("data/sub/")).isEqualTo("/sub");
    }

    /**
     * addition 字段解析与缺省回退。
     */
    @Test
    void additionParsing_appliesDefaults() {
        S3Driver driver = driver("{\"bucket\":\"b\",\"accessKeyId\":\"k\",\"secretAccessKey\":\"s\"}");
        S3DriverAddition addition = (S3DriverAddition) driver.addition();

        assertThat(addition.effectiveRegion()).isEqualTo("us-east-1");
        assertThat(addition.effectiveSignExpireSec()).isEqualTo(900);
        assertThat(addition.normalizedRootFolder()).isEmpty();
        assertThat(addition.pathStyle()).isFalse();
    }

    /**
     * 空 addition 抛 BAD_REQUEST。
     */
    @Test
    void blankAddition_isRejected() {
        S3Driver driver = new S3Driver(objectMapper);
        assertThatThrownBy(() -> driver.setStorage(storage("")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("S3 driver addition is required");
    }

    private S3Driver driver(String additionJson) {
        S3Driver driver = new S3Driver(objectMapper);
        driver.setStorage(storage(additionJson));
        return driver;
    }

    private Storage storage(String additionJson) {
        return new Storage(1L, "/s3", 0, "S3", 30, "work",
                additionJson, "", null,
                false, false, false, "name", "asc", "front", false, "proxy", false);
    }
}
