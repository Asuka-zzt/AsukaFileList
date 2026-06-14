package com.asuka.filelist.api.response;

import com.asuka.filelist.infrastructure.persistence.entity.KbDirectoryBatchEntity;

/** 目录入库批次进度响应。 */
public record KbDirectoryBatchResponse(
        Long id,
        Long kbId,
        String sourcePath,
        String status,
        int total,
        int added,
        int updated,
        int unchanged,
        int skipped,
        int failed,
        String errorMsg
) {

    public static KbDirectoryBatchResponse of(KbDirectoryBatchEntity e) {
        return new KbDirectoryBatchResponse(
                e.getId(), e.getKbId(), e.getSourcePath(), e.getStatus(),
                e.getTotal(), e.getAdded(), e.getUpdated(), e.getUnchanged(),
                e.getSkipped(), e.getFailed(), e.getErrorMsg());
    }
}
