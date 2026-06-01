package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.response.DriverInfoResponse;
import com.asuka.filelist.application.storage.DriverApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员驱动查询接口。
 */
@RestController
@RequestMapping("/api/admin/driver")
public class AdminDriverController {

    private final DriverApplicationService driverApplicationService;

    public AdminDriverController(DriverApplicationService driverApplicationService) {
        this.driverApplicationService = driverApplicationService;
    }

    /**
     * 查询驱动信息列表。
     */
    @GetMapping("/list")
    public ApiResponse<List<DriverInfoResponse>> list() {
        return ApiResponse.success(driverApplicationService.driverInfos());
    }

    /**
     * 查询驱动名称列表。
     */
    @GetMapping("/names")
    public ApiResponse<List<String>> names() {
        return ApiResponse.success(driverApplicationService.driverNames());
    }
}
