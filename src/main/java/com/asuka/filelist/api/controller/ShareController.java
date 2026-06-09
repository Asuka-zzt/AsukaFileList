package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.ShareCreateRequest;
import com.asuka.filelist.api.request.ShareDeleteRequest;
import com.asuka.filelist.api.request.ShareUpdateRequest;
import com.asuka.filelist.api.response.SharePageResponse;
import com.asuka.filelist.api.response.ShareResponse;
import com.asuka.filelist.application.share.ShareApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分享管理接口（登录态）：创建/更新/删除/列出当前用户的分享。
 */
@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareApplicationService shareApplicationService;

    public ShareController(ShareApplicationService shareApplicationService) {
        this.shareApplicationService = shareApplicationService;
    }

    /**
     * 创建分享。
     */
    @PostMapping("/create")
    public ApiResponse<ShareResponse> create(CurrentUser currentUser, @Valid @RequestBody ShareCreateRequest request) {
        return ApiResponse.success(shareApplicationService.create(currentUser, request));
    }

    /**
     * 更新分享控制项。
     */
    @PostMapping("/update")
    public ApiResponse<ShareResponse> update(CurrentUser currentUser, @Valid @RequestBody ShareUpdateRequest request) {
        return ApiResponse.success(shareApplicationService.update(currentUser, request));
    }

    /**
     * 删除分享。
     */
    @PostMapping("/delete")
    public ApiResponse<Void> delete(CurrentUser currentUser, @Valid @RequestBody ShareDeleteRequest request) {
        shareApplicationService.delete(currentUser, request);
        return ApiResponse.ok();
    }

    /**
     * 分页查询我的分享。
     */
    @GetMapping("/list")
    public ApiResponse<SharePageResponse> list(CurrentUser currentUser,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int perPage) {
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(Math.max(1, perPage), 200);
        return ApiResponse.success(shareApplicationService.list(currentUser, safePage, safePerPage));
    }
}
