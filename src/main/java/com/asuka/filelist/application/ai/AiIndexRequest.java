package com.asuka.filelist.application.ai;

public record AiIndexRequest(
        long userFileId,
        long userId,
        String fileDownloadUrl,
        String mimeType
) {
}
