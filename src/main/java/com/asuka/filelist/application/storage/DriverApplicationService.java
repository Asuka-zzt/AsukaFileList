package com.asuka.filelist.application.storage;

import com.asuka.filelist.api.response.DriverInfoResponse;
import com.asuka.filelist.infrastructure.driver.StorageDriverRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 驱动查询用例服务。
 */
@Service
public class DriverApplicationService {

    private final StorageDriverRegistry storageDriverRegistry;

    public DriverApplicationService(StorageDriverRegistry storageDriverRegistry) {
        this.storageDriverRegistry = storageDriverRegistry;
    }

    /**
     * 查询驱动名称列表。
     */
    public List<String> driverNames() {
        return storageDriverRegistry.driverNames().stream().toList();
    }

    /**
     * 查询驱动信息列表。
     */
    public List<DriverInfoResponse> driverInfos() {
        return storageDriverRegistry.driverInfos().stream().map(DriverInfoResponse::from).toList();
    }
}
