package com.asuka.filelist.api.response;

import java.util.List;

/**
 * 任务列表分页响应。
 */
public record TaskPageResponse(
        List<TaskResponse> content,
        long total,
        int page,
        int perPage
) {
}
