package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 文件名搜索请求。
 */
public record FsSearchRequest(
        @NotBlank String keyword,
        Integer page,
        Integer perPage
) {

    public int effectivePage() {
        return page == null || page < 1 ? 1 : page;
    }

    public int effectivePerPage() {
        if (perPage == null || perPage < 1) {
            return 50;
        }
        return Math.min(perPage, 200);
    }
}
