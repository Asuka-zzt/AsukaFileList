package com.asuka.filelist.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FsListRequest(
        String path,
        String password,
        boolean refresh,
        @Min(1) Integer page,
        @Min(-1) @Max(500) Integer perPage
) {

    public int effectivePage() {
        return page == null || page < 1 ? 1 : page;
    }

    public int effectivePerPage() {
        if (perPage == null || perPage == 0) {
            return 200;
        }
        return perPage;
    }
}
