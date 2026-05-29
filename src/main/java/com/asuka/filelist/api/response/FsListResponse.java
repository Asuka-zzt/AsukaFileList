package com.asuka.filelist.api.response;

import java.util.List;

public record FsListResponse(
        List<FileObjectResponse> content,
        long total,
        int page,
        int perPage,
        boolean hasMore,
        String readme,
        String header,
        boolean write,
        String provider
) {
}
