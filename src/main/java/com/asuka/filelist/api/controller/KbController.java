package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.KbAddDirectoryRequest;
import com.asuka.filelist.api.request.KbAddDocumentRequest;
import com.asuka.filelist.api.request.KbChatRequest;
import com.asuka.filelist.api.request.KbCreateRequest;
import com.asuka.filelist.api.response.KbDirectoryBatchResponse;
import com.asuka.filelist.api.response.KbDocumentResponse;
import com.asuka.filelist.api.response.KbResponse;
import com.asuka.filelist.application.ai.KbApplicationService;
import com.asuka.filelist.application.ai.KbDirectorySyncService;
import com.asuka.filelist.common.result.ApiResponse;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 知识库接口（登录态，全部校验 userId 归属）。
 * 问答接口以 SSE 把 AI 服务的流式结果透传给前端。
 */
@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final KbApplicationService kbApplicationService;
    private final KbDirectorySyncService kbDirectorySyncService;

    public KbController(KbApplicationService kbApplicationService,
                        KbDirectorySyncService kbDirectorySyncService) {
        this.kbApplicationService = kbApplicationService;
        this.kbDirectorySyncService = kbDirectorySyncService;
    }

    /** 创建知识库。 */
    @PostMapping
    public ApiResponse<KbResponse> create(CurrentUser currentUser, @Valid @RequestBody KbCreateRequest request) {
        return ApiResponse.success(kbApplicationService.createKb(currentUser, request));
    }

    /** 列出我的知识库。 */
    @GetMapping
    public ApiResponse<List<KbResponse>> list(CurrentUser currentUser) {
        return ApiResponse.success(kbApplicationService.listKbs(currentUser));
    }

    /** 删除知识库（级联清 LightRAG workspace）。 */
    @DeleteMapping("/{kbId}")
    public ApiResponse<Void> delete(CurrentUser currentUser, @PathVariable long kbId) {
        kbApplicationService.deleteKb(currentUser, kbId);
        return ApiResponse.ok();
    }

    /** 把网盘文件加入知识库（触发解析+索引任务）。 */
    @PostMapping("/{kbId}/documents")
    public ApiResponse<KbDocumentResponse> addDocument(CurrentUser currentUser,
                                                       @PathVariable long kbId,
                                                       @Valid @RequestBody KbAddDocumentRequest request) {
        return ApiResponse.success(kbApplicationService.addDocument(currentUser, kbId, request));
    }

    /** 把网盘目录下的受支持文件批量加入知识库（异步批次+增量同步），立即返回批次进度。 */
    @PostMapping("/{kbId}/documents/directory")
    public ApiResponse<KbDirectoryBatchResponse> addDirectory(CurrentUser currentUser,
                                                              @PathVariable long kbId,
                                                              @Valid @RequestBody KbAddDirectoryRequest request) {
        long batchId = kbDirectorySyncService.start(currentUser, kbId, request);
        return ApiResponse.success(kbDirectorySyncService.getBatch(currentUser, kbId, batchId));
    }

    /** 查询目录入库批次进度。 */
    @GetMapping("/{kbId}/batches/{batchId}")
    public ApiResponse<KbDirectoryBatchResponse> getBatch(CurrentUser currentUser,
                                                          @PathVariable long kbId,
                                                          @PathVariable long batchId) {
        return ApiResponse.success(kbDirectorySyncService.getBatch(currentUser, kbId, batchId));
    }

    /** 列出知识库文档与索引状态。 */
    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<KbDocumentResponse>> listDocuments(CurrentUser currentUser, @PathVariable long kbId) {
        return ApiResponse.success(kbApplicationService.listDocuments(currentUser, kbId));
    }

    /** 移除知识库文档。 */
    @DeleteMapping("/{kbId}/documents/{docId}")
    public ApiResponse<Void> deleteDocument(CurrentUser currentUser,
                                            @PathVariable long kbId,
                                            @PathVariable long docId) {
        kbApplicationService.deleteDocument(currentUser, kbId, docId);
        return ApiResponse.ok();
    }

    /** 整库问答（SSE）。 */
    @PostMapping("/{kbId}/chat")
    public void chat(CurrentUser currentUser,
                     @PathVariable long kbId,
                     @Valid @RequestBody KbChatRequest request,
                     HttpServletResponse response) throws IOException {
        prepareSse(response);
        kbApplicationService.streamChat(currentUser, kbId, request, response.getOutputStream());
    }

    /** 单文档问答（SSE）。 */
    @PostMapping("/{kbId}/documents/{docId}/chat")
    public void chatDocument(CurrentUser currentUser,
                             @PathVariable long kbId,
                             @PathVariable long docId,
                             @Valid @RequestBody KbChatRequest request,
                             HttpServletResponse response) throws IOException {
        prepareSse(response);
        kbApplicationService.streamDocumentChat(currentUser, kbId, docId, request, response.getOutputStream());
    }

    /** 配置 SSE 响应头。 */
    private void prepareSse(HttpServletResponse response) {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }
}
