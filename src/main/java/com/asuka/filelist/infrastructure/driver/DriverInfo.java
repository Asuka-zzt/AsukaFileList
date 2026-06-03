package com.asuka.filelist.infrastructure.driver;

import java.util.List;

/**
 * 驱动元信息和配置项描述。
 */
public record DriverInfo(
        DriverConfig config,
        List<DriverItem> items
) {
}
