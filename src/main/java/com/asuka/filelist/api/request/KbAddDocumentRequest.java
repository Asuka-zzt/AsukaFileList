package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 把网盘文件加入知识库请求。
 *
 * @param path    网盘文件路径（用户可见路径）
 * @param docType paper | book | note（可空，默认 paper）
 */
public record KbAddDocumentRequest(
        @NotBlank String path,
        String docType
) {
}
