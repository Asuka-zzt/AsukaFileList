package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fs")
public class FsController {

    private final FsApplicationService fsApplicationService;

    public FsController(FsApplicationService fsApplicationService) {
        this.fsApplicationService = fsApplicationService;
    }

    @PostMapping("/list")
    public ApiResponse<FsListResponse> list(@Valid @RequestBody FsListRequest request) {
        return ApiResponse.success(fsApplicationService.list(request));
    }
}
