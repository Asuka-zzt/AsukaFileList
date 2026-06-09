package com.asuka.filelist.api.response;

/**
 * 文件名搜索结果项（path 为当前用户可见路径）。
 */
public record SearchResultResponse(
        String path,
        String name,
        boolean isDir,
        long size
) {
}
