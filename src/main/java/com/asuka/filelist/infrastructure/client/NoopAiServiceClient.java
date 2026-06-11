package com.asuka.filelist.infrastructure.client;

import com.asuka.filelist.application.ai.AiKbChatRequest;
import com.asuka.filelist.application.ai.AiKbIndexRequest;
import com.asuka.filelist.application.ai.AiKbTaskResponse;
import com.asuka.filelist.application.ai.AiServiceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 空实现：AI 服务不可用时的降级桩（单测 / 离线开发）。
 * 仅当 {@code asuka.ai.enabled=false} 时启用；否则使用 {@link HttpAiServiceClient}。
 */
@Component
@ConditionalOnProperty(prefix = "asuka.ai", name = "enabled", havingValue = "false")
public class NoopAiServiceClient implements AiServiceClient {

    @Override
    public AiKbTaskResponse submitKbIndex(long kbId, AiKbIndexRequest request) {
        return new AiKbTaskResponse("noop", "pending", null);
    }

    @Override
    public void deleteKb(long kbId) {
        // no-op
    }

    @Override
    public void deleteKbDocument(long kbId, String docId) {
        // no-op
    }

    @Override
    public AiKbTaskResponse getKbTask(String taskId) {
        return new AiKbTaskResponse(taskId, "indexed", null);
    }

    @Override
    public void streamChat(long kbId, AiKbChatRequest request, OutputStream out) {
        try {
            out.write("data: AI service disabled (noop)\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
