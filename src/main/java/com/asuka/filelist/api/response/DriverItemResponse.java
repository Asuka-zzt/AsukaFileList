package com.asuka.filelist.api.response;

import com.asuka.filelist.infrastructure.driver.DriverItem;

/**
 * 驱动配置项响应。
 */
public record DriverItemResponse(
        String name,
        String label,
        String type,
        boolean required,
        String defaultValue,
        String description
) {

    /**
     * 从驱动配置项描述转换。
     */
    public static DriverItemResponse from(DriverItem item) {
        return new DriverItemResponse(
                item.name(),
                item.label(),
                item.type(),
                item.required(),
                item.defaultValue(),
                item.description()
        );
    }
}
