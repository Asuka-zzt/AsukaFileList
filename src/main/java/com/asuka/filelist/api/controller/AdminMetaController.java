package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.MetaCreateRequest;
import com.asuka.filelist.api.request.MetaIdRequest;
import com.asuka.filelist.api.request.MetaUpdateRequest;
import com.asuka.filelist.api.response.MetaRuleResponse;
import com.asuka.filelist.application.meta.MetaApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员目录 Meta 规则管理接口（/api/admin/** 由拦截器强制 admin）。
 */
@RestController
@RequestMapping("/api/admin/meta")
public class AdminMetaController {

    private final MetaApplicationService metaApplicationService;

    public AdminMetaController(MetaApplicationService metaApplicationService) {
        this.metaApplicationService = metaApplicationService;
    }

    /**
     * 查询 Meta 列表。
     */
    @GetMapping("/list")
    public ApiResponse<List<MetaRuleResponse>> list() {
        return ApiResponse.success(metaApplicationService.list());
    }

    /**
     * 查询 Meta 详情。
     */
    @GetMapping("/get")
    public ApiResponse<MetaRuleResponse> get(@RequestParam("id") Long id) {
        return ApiResponse.success(metaApplicationService.get(id));
    }

    /**
     * 创建 Meta。
     */
    @PostMapping("/create")
    public ApiResponse<MetaRuleResponse> create(@Valid @RequestBody MetaCreateRequest request) {
        return ApiResponse.success(metaApplicationService.create(request));
    }

    /**
     * 更新 Meta。
     */
    @PostMapping("/update")
    public ApiResponse<MetaRuleResponse> update(@Valid @RequestBody MetaUpdateRequest request) {
        return ApiResponse.success(metaApplicationService.update(request));
    }

    /**
     * 删除 Meta。
     */
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody MetaIdRequest request) {
        metaApplicationService.delete(request.id());
        return ApiResponse.ok();
    }
}
