package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 公开分享目录列表请求。{@code subPath} 为相对分享根的子路径。
 */
public record PublicShareListRequest(
        @NotBlank String shareId,
        String subPath,
        Integer page,
        Integer perPage
) {

    public int effectivePage() {
        return page == null || page < 1 ? 1 : page;
    }

    public int effectivePerPage() {
        if (perPage == null || perPage == 0) {
            return 200;
        }
        return Math.min(Math.max(perPage, -1), 500);
    }
}
