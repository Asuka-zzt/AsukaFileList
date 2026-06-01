package com.asuka.filelist.application.storage;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.StorageDriver;
import com.asuka.filelist.infrastructure.driver.StorageDriverFactory;
import com.asuka.filelist.infrastructure.driver.StorageDriverRegistry;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存存储挂载表，负责驱动运行时生命周期和最长前缀匹配。
 */
@Component
public class MountedStorageRegistry {

    private final StorageDriverRegistry driverRegistry;
    private final Map<Long, MountedStorageRuntime> runtimes = new ConcurrentHashMap<>();

    public MountedStorageRegistry(StorageDriverRegistry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    /**
     * 创建并初始化运行时，但不写入挂载表。
     */
    public MountedStorageRuntime createRuntime(Storage storage) {
        StorageDriverFactory factory = driverRegistry.findFactory(storage.driver())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Driver does not exist"));
        StorageDriver driver = factory.create();
        driver.setStorage(storage);
        driver.init(new DriverContext(storage.mountPath(), Map.of()));
        return new MountedStorageRuntime(storage, driver);
    }

    /**
     * 创建运行时并替换挂载表中的同 ID storage。
     */
    public void mount(Storage storage) {
        replace(createRuntime(storage));
    }

    /**
     * 替换运行时，旧运行时会先 drop。
     */
    public void replace(MountedStorageRuntime runtime) {
        unmount(runtime.storage().id());
        runtimes.put(runtime.storage().id(), runtime);
    }

    /**
     * 卸载指定存储。
     */
    public void unmount(Long storageId) {
        MountedStorageRuntime runtime = runtimes.remove(storageId);
        if (runtime != null) {
            runtime.driver().drop(new DriverContext(runtime.storage().mountPath(), Map.of()));
        }
    }

    /**
     * 按最长挂载路径前缀匹配运行时。
     */
    public Optional<MountedStorageRuntime> matchLongestPrefix(String rawPath) {
        String path = PathUtils.fixAndCleanPath(rawPath);
        return runtimes.values().stream()
                .filter(runtime -> PathUtils.isSubPath(runtime.storage().mountPath(), path))
                .max(Comparator.comparingInt(runtime -> runtime.storage().mountPath().length()));
    }

    /**
     * 返回当前所有挂载运行时。
     */
    public List<MountedStorageRuntime> listMounts() {
        return runtimes.values().stream()
                .sorted(Comparator.comparing(runtime -> runtime.storage().mountPath()))
                .toList();
    }
}
