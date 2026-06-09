package com.asuka.filelist.api.response;

import java.util.List;

/**
 * 我的分享分页响应。
 */
public record SharePageResponse(
        List<ShareResponse> content,
        long total,
        int page,
        int perPage
) {
}
