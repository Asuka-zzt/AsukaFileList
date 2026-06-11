package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建知识库请求。 */
public record KbCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description
) {
}
