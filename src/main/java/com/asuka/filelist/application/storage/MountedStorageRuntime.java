package com.asuka.filelist.application.storage;

import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.StorageDriver;

/**
 * 已初始化并挂载到内存表的存储运行时。
 */
public record MountedStorageRuntime(
        Storage storage,
        StorageDriver driver
) {
}
