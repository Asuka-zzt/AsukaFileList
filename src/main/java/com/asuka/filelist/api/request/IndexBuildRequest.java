package com.asuka.filelist.api.request;

/**
 * 重建文件名索引请求。storageId 为空表示重建全部已挂载存储。
 */
public record IndexBuildRequest(
        Long storageId
) {
}
