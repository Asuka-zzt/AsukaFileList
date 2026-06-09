package com.asuka.filelist.api.response;

import java.util.List;

/**
 * 文件名搜索分页响应。total 为索引命中总数（权限过滤前），content 为本页有权访问项。
 */
public record SearchPageResponse(
        List<SearchResultResponse> content,
        long total,
        int page,
        int perPage
) {
}
