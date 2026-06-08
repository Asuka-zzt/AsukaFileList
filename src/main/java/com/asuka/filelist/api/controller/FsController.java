package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.FsCopyRequest;
import com.asuka.filelist.api.request.FsDirsRequest;
import com.asuka.filelist.api.request.FsGetRequest;
import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.request.FsMkdirRequest;
import com.asuka.filelist.api.request.FsMoveRequest;
import com.asuka.filelist.api.request.FsRemoveRequest;
import com.asuka.filelist.api.request.FsRenameRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.common.result.ApiResponse;
import com.asuka.filelist.infrastructure.driver.UploadFile;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件系统接口，提供读写与上传能力。
 */
@RestController
@RequestMapping("/api/fs")
public class FsController {

    private final FsApplicationService fsApplicationService;

    public FsController(FsApplicationService fsApplicationService) {
        this.fsApplicationService = fsApplicationService;
    }

    @PostMapping("/list")
    public ApiResponse<FsListResponse> list(CurrentUser currentUser, @Valid @RequestBody FsListRequest request) {
        return ApiResponse.success(fsApplicationService.list(currentUser, request));
    }

    @PostMapping("/get")
    public ApiResponse<FileObjectResponse> get(CurrentUser currentUser, @Valid @RequestBody FsGetRequest request) {
        return ApiResponse.success(fsApplicationService.get(currentUser, request));
    }

    @PostMapping("/dirs")
    public ApiResponse<List<FileObjectResponse>> dirs(CurrentUser currentUser, @Valid @RequestBody FsDirsRequest request) {
        return ApiResponse.success(fsApplicationService.dirs(currentUser, request));
    }

    @PostMapping("/mkdir")
    public ApiResponse<FileObjectResponse> mkdir(CurrentUser currentUser, @Valid @RequestBody FsMkdirRequest request) {
        return ApiResponse.success(fsApplicationService.mkdir(currentUser, request));
    }

    @PostMapping("/rename")
    public ApiResponse<Void> rename(CurrentUser currentUser, @Valid @RequestBody FsRenameRequest request) {
        fsApplicationService.rename(currentUser, request);
        return ApiResponse.ok();
    }

    @PostMapping("/move")
    public ApiResponse<Void> move(CurrentUser currentUser, @Valid @RequestBody FsMoveRequest request) {
        fsApplicationService.move(currentUser, request);
        return ApiResponse.ok();
    }

    @PostMapping("/copy")
    public ApiResponse<Void> copy(CurrentUser currentUser, @Valid @RequestBody FsCopyRequest request) {
        fsApplicationService.copy(currentUser, request);
        return ApiResponse.ok();
    }

    @PostMapping("/remove")
    public ApiResponse<Void> remove(CurrentUser currentUser, @Valid @RequestBody FsRemoveRequest request) {
        fsApplicationService.remove(currentUser, request);
        return ApiResponse.ok();
    }

    /**
     * 流式上传，目标文件完整路径经 File-Path 头传入，请求体为文件内容。
     */
    @PutMapping("/put")
    public ApiResponse<FileObjectResponse> put(CurrentUser currentUser, HttpServletRequest request) throws IOException {
        String cleanPath = PathUtils.fixAndCleanPath(decodeFilePath(request.getHeader("File-Path")));
        if ("/".equals(cleanPath)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File-Path must point to a file");
        }
        int idx = cleanPath.lastIndexOf('/');
        String dir = idx <= 0 ? "/" : cleanPath.substring(0, idx);
        String name = cleanPath.substring(idx + 1);
        UploadFile file = new UploadFile(name, request.getContentLengthLong(),
                request.getContentType(), request.getInputStream());
        return ApiResponse.success(fsApplicationService.put(currentUser, dir, file));
    }

    /**
     * 解码 File-Path 头，避免非 ASCII 文件名乱码。
     */
    private String decodeFilePath(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File-Path header is required");
        }
        return URLDecoder.decode(rawHeader, StandardCharsets.UTF_8);
    }
}
