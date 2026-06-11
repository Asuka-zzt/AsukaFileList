package com.asuka.filelist.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** 知识库问答请求（整库 / 单文档共用，单文档时 docId 来自路径）。 */
public record KbChatRequest(
        @NotBlank String question,
        List<Message> history
) {

    /** 历史消息：role = user | assistant。 */
    public record Message(String role, String content) {
    }
}
