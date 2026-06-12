package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 把一个网盘目录下的受支持文件批量加入知识库（异步批次 + 增量同步）。
 *
 * @param path      网盘目录路径（用户可见路径）
 * @param docType   paper | book | note（可空，默认 paper）
 * @param recursive 是否递归子目录（可空，默认 true）
 */
public record KbAddDirectoryRequest(
        @NotBlank String path,
        String docType,
        Boolean recursive
) {
}
