package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.KbIndexCallbackRequest;
import com.asuka.filelist.application.ai.KbApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import com.asuka.filelist.infrastructure.security.InternalTokenGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库内部接口（仅 AI 服务调用，master token 鉴权）：索引状态回写。
 */
@RestController
public class InternalKbController {

    private final InternalTokenGuard internalTokenGuard;
    private final KbApplicationService kbApplicationService;

    public InternalKbController(InternalTokenGuard internalTokenGuard,
                                KbApplicationService kbApplicationService) {
        this.internalTokenGuard = internalTokenGuard;
        this.kbApplicationService = kbApplicationService;
    }

    /** AI 服务回写文档索引状态。 */
    @PostMapping("/internal/kb/index-callback")
    public ApiResponse<Void> indexCallback(HttpServletRequest request,
                                           @Valid @RequestBody KbIndexCallbackRequest body) {
        internalTokenGuard.verify(request);
        kbApplicationService.updateDocumentStatus(
                body.docId(), body.status(), body.lightragDocId(), body.error());
        return ApiResponse.ok();
    }
}
