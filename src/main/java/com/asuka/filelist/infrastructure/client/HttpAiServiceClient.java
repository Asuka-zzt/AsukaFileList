package com.asuka.filelist.infrastructure.client;

import com.asuka.filelist.application.ai.AiKbChatRequest;
import com.asuka.filelist.application.ai.AiKbIndexRequest;
import com.asuka.filelist.application.ai.AiKbTaskResponse;
import com.asuka.filelist.application.ai.AiServiceClient;
import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AI 服务 HTTP 客户端（内网，X-API-Key 鉴权）。
 *
 * <p>普通调用走 JSON 请求/响应；问答接口把 AI 服务的 SSE 流原样透传到输出流。
 * 默认启用；设 {@code asuka.ai.enabled=false} 时改用 {@link NoopAiServiceClient} 降级桩。
 */
@Component
@ConditionalOnProperty(prefix = "asuka.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HttpAiServiceClient implements AiServiceClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public HttpAiServiceClient(AsukaProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(properties.ai().baseUrl());
        this.apiKey = properties.ai().apiKey();
        this.objectMapper = objectMapper;
    }

    @Override
    public AiKbTaskResponse submitKbIndex(long kbId, AiKbIndexRequest request) {
        String body = sendJson("POST", "/kb/" + kbId + "/index", request);
        return readValue(body, AiKbTaskResponse.class);
    }

    @Override
    public void deleteKb(long kbId) {
        sendJson("DELETE", "/kb/" + kbId, null);
    }

    @Override
    public void deleteKbDocument(long kbId, String docId) {
        sendJson("DELETE", "/kb/" + kbId + "/doc/" + docId, null);
    }

    @Override
    public AiKbTaskResponse getKbTask(String taskId) {
        String body = sendJson("GET", "/kb/task/" + taskId, null);
        return readValue(body, AiKbTaskResponse.class);
    }

    @Override
    public void streamChat(long kbId, AiKbChatRequest request, OutputStream out) {
        HttpRequest httpRequest = newRequest("/kb/" + kbId + "/chat")
                .timeout(Duration.ofSeconds(300))
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(request)))
                .build();
        try {
            HttpResponse<InputStream> resp =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                        "AI chat failed: HTTP " + resp.statusCode());
            }
            pump(resp.body(), out);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI chat stream error: " + e.getMessage());
        }
    }

    // ─── 内部工具 ──────────────────────────────────────────────

    /** 发送一个 JSON 请求，返回响应体字符串；非 2xx 抛 AI_SERVICE_ERROR。 */
    private String sendJson(String method, String path, Object payload) {
        HttpRequest.BodyPublisher publisher = payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(writeJson(payload));
        HttpRequest request = newRequest(path)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                        "AI service " + method + " " + path + " -> HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service request failed: " + e.getMessage());
        }
    }

    private HttpRequest.Builder newRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("X-API-Key", apiKey);
    }

    private void pump(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            out.flush();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Failed to serialize AI request");
        }
    }

    private <T> T readValue(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Failed to parse AI response");
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
