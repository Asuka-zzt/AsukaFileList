package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.PublicShareGetRequest;
import com.asuka.filelist.api.request.PublicShareListRequest;
import com.asuka.filelist.api.request.ShareAuthRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.api.response.PublicShareInfoResponse;
import com.asuka.filelist.api.response.ShareAuthResponse;
import com.asuka.filelist.application.share.ShareApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开分享接口（匿名）：元信息、密码校验、目录列表、文件详情。
 *
 * <p>list/get 需携带 {@code X-Share-Token}（由 auth 签发）。该路径在认证拦截器中放行。
 */
@RestController
@RequestMapping("/api/public/share")
public class PublicShareController {

    private static final String TOKEN_HEADER = "X-Share-Token";

    private final ShareApplicationService shareApplicationService;

    public PublicShareController(ShareApplicationService shareApplicationService) {
        this.shareApplicationService = shareApplicationService;
    }

    /**
     * 分享元信息（是否需要密码等）。
     */
    @GetMapping("/info")
    public ApiResponse<PublicShareInfoResponse> info(@RequestParam String shareId) {
        return ApiResponse.success(shareApplicationService.info(shareId));
    }

    /**
     * 密码校验，成功返回访问令牌。
     */
    @PostMapping("/auth")
    public ApiResponse<ShareAuthResponse> auth(@Valid @RequestBody ShareAuthRequest request) {
        return ApiResponse.success(shareApplicationService.auth(request));
    }

    /**
     * 分享目录列表。
     */
    @PostMapping("/list")
    public ApiResponse<FsListResponse> list(@Valid @RequestBody PublicShareListRequest request,
                                            @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return ApiResponse.success(shareApplicationService.listPublic(request, token));
    }

    /**
     * 分享文件详情。
     */
    @PostMapping("/get")
    public ApiResponse<FileObjectResponse> get(@Valid @RequestBody PublicShareGetRequest request,
                                               @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return ApiResponse.success(shareApplicationService.getPublic(request, token));
    }
}
