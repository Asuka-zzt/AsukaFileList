package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.IndexBuildRequest;
import com.asuka.filelist.application.search.FileNameIndexService;
import com.asuka.filelist.common.result.ApiResponse;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员文件名索引管理接口（/api/admin/** 由拦截器强制 admin）。
 */
@RestController
@RequestMapping("/api/admin/index")
public class AdminIndexController {

    private final FileNameIndexService fileNameIndexService;

    public AdminIndexController(FileNameIndexService fileNameIndexService) {
        this.fileNameIndexService = fileNameIndexService;
    }

    /**
     * 异步重建文件名索引，返回任务 id（storageId 为空则全部存储）。
     */
    @PostMapping("/build")
    public ApiResponse<Long> build(CurrentUser currentUser, @RequestBody(required = false) IndexBuildRequest request) {
        Long storageId = request == null ? null : request.storageId();
        return ApiResponse.success(fileNameIndexService.submitBuild(currentUser, storageId));
    }
}
