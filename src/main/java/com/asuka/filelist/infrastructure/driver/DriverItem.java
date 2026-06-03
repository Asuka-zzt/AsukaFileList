package com.asuka.filelist.infrastructure.driver;

/**
 * 驱动配置项描述，供管理端渲染配置表单。
 */
public record DriverItem(
        String name,
        String label,
        String type,
        boolean required,
        String defaultValue,
        String description
) {
}
