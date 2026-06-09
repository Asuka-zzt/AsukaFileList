package com.asuka.filelist.infrastructure.driver.s3;

import com.asuka.filelist.infrastructure.driver.DriverConfig;
import com.asuka.filelist.infrastructure.driver.DriverInfo;
import com.asuka.filelist.infrastructure.driver.DriverItem;
import com.asuka.filelist.infrastructure.driver.StorageDriver;
import com.asuka.filelist.infrastructure.driver.StorageDriverFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AWS S3（及 S3 兼容）驱动工厂。
 */
@Component
public class S3DriverFactory implements StorageDriverFactory {

    public static final String DRIVER_NAME = "S3";

    private final ObjectMapper objectMapper;

    public S3DriverFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return DRIVER_NAME;
    }

    /**
     * 驱动配置项，供管理端动态渲染表单。
     */
    @Override
    public DriverInfo info() {
        DriverConfig config = new DriverConfig(DRIVER_NAME, false, false, false, false, false, "/", true);
        List<DriverItem> items = List.of(
                new DriverItem("bucket", "Bucket", "string", true, "", "S3 bucket name"),
                new DriverItem("endpoint", "Endpoint", "string", false, "", "S3-compatible endpoint; empty for AWS"),
                new DriverItem("region", "Region", "string", true, "us-east-1", "AWS region"),
                new DriverItem("accessKeyId", "Access key id", "string", true, "", "Access key"),
                new DriverItem("secretAccessKey", "Secret access key", "string", true, "", "Secret key"),
                new DriverItem("rootFolder", "Root folder", "string", false, "", "Prefix inside bucket; empty for bucket root"),
                new DriverItem("pathStyle", "Path style", "boolean", false, "false", "Path-style addressing (MinIO etc.)"),
                new DriverItem("signExpireSec", "Sign expire seconds", "integer", false, "900", "Presigned URL TTL in seconds")
        );
        return new DriverInfo(config, items);
    }

    @Override
    public StorageDriver create() {
        return new S3Driver(objectMapper);
    }
}
