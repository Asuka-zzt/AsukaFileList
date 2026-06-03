package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.StorageCreateRequest;
import com.asuka.filelist.api.request.StorageIdRequest;
import com.asuka.filelist.api.request.StorageUpdateRequest;
import com.asuka.filelist.api.response.StorageResponse;
import com.asuka.filelist.application.storage.StorageApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员存储挂载管理接口。
 */
@RestController
@RequestMapping("/api/admin/storage")
public class AdminStorageController {

    private final StorageApplicationService storageApplicationService;

    public AdminStorageController(StorageApplicationService storageApplicationService) {
        this.storageApplicationService = storageApplicationService;
    }

    /**
     * 查询存储列表。
     */
    @GetMapping("/list")
    public ApiResponse<List<StorageResponse>> list() {
        return ApiResponse.success(storageApplicationService.list());
    }

    /**
     * 创建存储。
     */
    @PostMapping("/create")
    public ApiResponse<StorageResponse> create(@Valid @RequestBody StorageCreateRequest request) {
        return ApiResponse.success(storageApplicationService.create(request));
    }

    /**
     * 更新存储。
     */
    @PostMapping("/update")
    public ApiResponse<StorageResponse> update(@Valid @RequestBody StorageUpdateRequest request) {
        return ApiResponse.success(storageApplicationService.update(request));
    }

    /**
     * 启用存储。
     */
    @PostMapping("/enable")
    public ApiResponse<StorageResponse> enable(@Valid @RequestBody StorageIdRequest request) {
        return ApiResponse.success(storageApplicationService.enable(request.id()));
    }

    /**
     * 禁用存储。
     */
    @PostMapping("/disable")
    public ApiResponse<StorageResponse> disable(@Valid @RequestBody StorageIdRequest request) {
        return ApiResponse.success(storageApplicationService.disable(request.id()));
    }

    /**
     * 删除存储。
     */
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody StorageIdRequest request) {
        storageApplicationService.delete(request.id());
        return ApiResponse.ok();
    }
}
