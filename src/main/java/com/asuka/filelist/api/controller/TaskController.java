package com.asuka.filelist.api.controller;

import com.asuka.filelist.api.response.TaskPageResponse;
import com.asuka.filelist.api.response.TaskResponse;
import com.asuka.filelist.application.task.TaskApplicationService;
import com.asuka.filelist.common.result.ApiResponse;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务中心接口：查询/取消当前用户的任务。
 */
@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskApplicationService taskApplicationService;

    public TaskController(TaskApplicationService taskApplicationService) {
        this.taskApplicationService = taskApplicationService;
    }

    /**
     * 分页查询我的任务，可按状态过滤。
     */
    @GetMapping("/list")
    public ApiResponse<TaskPageResponse> list(CurrentUser currentUser,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int perPage) {
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(Math.max(1, perPage), 200);
        return ApiResponse.success(taskApplicationService.list(currentUser, status, safePage, safePerPage));
    }

    /**
     * 任务详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(CurrentUser currentUser, @PathVariable Long id) {
        return ApiResponse.success(taskApplicationService.get(currentUser, id));
    }

    /**
     * 取消任务。
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(CurrentUser currentUser, @PathVariable Long id) {
        taskApplicationService.cancel(currentUser, id);
        return ApiResponse.ok();
    }
}
