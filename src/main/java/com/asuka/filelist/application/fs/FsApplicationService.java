package com.asuka.filelist.application.fs;

import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.common.path.PathUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class FsApplicationService {

    public FsListResponse list(FsListRequest request) {
        String normalizedPath = PathUtils.fixAndCleanPath(request.path());
        int page = request.effectivePage();
        int perPage = request.effectivePerPage();

        FileObjectResponse root = new FileObjectResponse(
                "root",
                normalizedPath,
                "/",
                0L,
                true,
                Instant.EPOCH,
                Instant.EPOCH,
                "",
                "",
                1,
                Map.of(),
                "virtual"
        );

        return new FsListResponse(
                List.of(root),
                1L,
                page,
                perPage,
                false,
                "",
                "",
                false,
                "virtual"
        );
    }
}
