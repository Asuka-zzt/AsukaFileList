package com.asuka.filelist.infrastructure.driver.local;

import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.infrastructure.driver.DriverConfig;
import com.asuka.filelist.infrastructure.driver.DriverInfo;
import com.asuka.filelist.infrastructure.driver.DriverItem;
import com.asuka.filelist.infrastructure.driver.StorageDriver;
import com.asuka.filelist.infrastructure.driver.StorageDriverFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local 本地文件系统驱动工厂。
 */
@Component
public class LocalDriverFactory implements StorageDriverFactory {

    public static final String DRIVER_NAME = "Local";

    private final ObjectMapper objectMapper;
    private final AsukaProperties properties;

    public LocalDriverFactory(ObjectMapper objectMapper, AsukaProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 驱动名称。
     */
    @Override
    public String name() {
        return DRIVER_NAME;
    }

    /**
     * 驱动配置描述。
     */
    @Override
    public DriverInfo info() {
        DriverConfig config = new DriverConfig(DRIVER_NAME, true, true, false, false, false, "/", true);
        DriverItem rootPath = new DriverItem(
                "rootPath",
                "Root path",
                "string",
                true,
                "",
                "Local filesystem root path under asuka.storage.local-root-whitelist"
        );
        return new DriverInfo(config, List.of(rootPath));
    }

    /**
     * 创建新的驱动实例。
     */
    @Override
    public StorageDriver create() {
        return new LocalDriver(objectMapper, properties.storage().localRootWhitelist());
    }
}
