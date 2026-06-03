package com.asuka.filelist.api.response;

import com.asuka.filelist.infrastructure.driver.DriverConfig;
import com.asuka.filelist.infrastructure.driver.DriverInfo;

import java.util.List;

/**
 * 驱动信息响应。
 */
public record DriverInfoResponse(
        String name,
        boolean localSort,
        boolean onlyLocal,
        boolean onlyProxy,
        boolean noCache,
        boolean noUpload,
        String defaultRoot,
        boolean checkStatus,
        List<DriverItemResponse> items
) {

    /**
     * 从驱动描述转换。
     */
    public static DriverInfoResponse from(DriverInfo info) {
        DriverConfig config = info.config();
        return new DriverInfoResponse(
                config.name(),
                config.localSort(),
                config.onlyLocal(),
                config.onlyProxy(),
                config.noCache(),
                config.noUpload(),
                config.defaultRoot(),
                config.checkStatus(),
                info.items().stream().map(DriverItemResponse::from).toList()
        );
    }
}
