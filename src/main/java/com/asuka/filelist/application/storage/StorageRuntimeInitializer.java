package com.asuka.filelist.application.storage;

import com.asuka.filelist.common.exception.BusinessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时加载已启用的存储挂载。
 */
@Component
public class StorageRuntimeInitializer implements ApplicationRunner {

    private static final String STATUS_WORK = "work";
    private static final String STATUS_INIT_ERROR = "init_error";

    private final StorageApplicationService storageApplicationService;
    private final MountedStorageRegistry mountedStorageRegistry;

    public StorageRuntimeInitializer(
            StorageApplicationService storageApplicationService,
            MountedStorageRegistry mountedStorageRegistry
    ) {
        this.storageApplicationService = storageApplicationService;
        this.mountedStorageRegistry = mountedStorageRegistry;
    }

    /**
     * 加载启用中的存储；单条失败不阻塞应用启动。
     */
    @Override
    public void run(ApplicationArguments args) {
        storageApplicationService.enabledStorages().forEach(storage -> {
            try {
                mountedStorageRegistry.mount(storage);
                storageApplicationService.updateStatus(storage.id(), STATUS_WORK);
            } catch (BusinessException ex) {
                storageApplicationService.updateStatus(storage.id(), STATUS_INIT_ERROR);
            }
        });
    }
}
