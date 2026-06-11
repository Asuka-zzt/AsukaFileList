package com.asuka.filelist.infrastructure.client;

import com.asuka.filelist.application.ai.AiKbChatRequest;
import com.asuka.filelist.application.ai.AiKbIndexRequest;
import com.asuka.filelist.application.ai.AiKbTaskResponse;
import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Python AI HTTP contract: paths, API key, JSON payload, SSE pass-through, and error mapping.
 */
class HttpAiServiceClientTest {

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"taskId\":\"task-1\",\"status\":\"pending\",\"error\":null}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Index requests use the internal path, API key, and camelCase JSON contract. */
    @Test
    void submitIndexUsesInternalContract() {
        AiKbTaskResponse response = client().submitKbIndex(12,
                new AiKbIndexRequest("doc-12", "http://java/file", "text/markdown", "note.md", "note"));

        assertThat(response.taskId()).isEqualTo("task-1");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/kb/12/index");
        assertThat(apiKey.get()).isEqualTo("test-key");
        assertThat(requestBody.get()).contains("\"docId\":\"doc-12\"", "\"fileName\":\"note.md\"");
    }

    /** Delete endpoints preserve KB and document identifiers. */
    @Test
    void deleteEndpointsUseExpectedPaths() {
        responseBody = "{\"taskId\":null,\"status\":\"deleted\",\"error\":null}";
        HttpAiServiceClient client = client();

        client.deleteKb(7);
        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(path.get()).isEqualTo("/kb/7");

        client.deleteKbDocument(7, "doc-7");
        assertThat(path.get()).isEqualTo("/kb/7/doc/doc-7");
    }

    /** Chat copies the upstream SSE stream byte-for-byte. */
    @Test
    void streamChatPassesThroughSse() {
        responseBody = "data: {\"type\":\"token\",\"text\":\"hello\"}\n\n"
                + "data: {\"type\":\"done\"}\n\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        client().streamChat(5, new AiKbChatRequest(
                "question", "doc-5",
                List.of(new AiKbChatRequest.AiKbChatMessage("user", "previous"))), output);

        assertThat(path.get()).isEqualTo("/kb/5/chat");
        assertThat(requestBody.get()).contains("\"docId\":\"doc-5\"", "\"question\":\"question\"");
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo(responseBody);
    }

    /** Non-success responses map to the stable AI_SERVICE_ERROR code. */
    @Test
    void nonSuccessResponseMapsToBusinessException() {
        responseStatus = 503;
        responseBody = "unavailable";

        assertThatThrownBy(() -> client().getKbTask("task-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_ERROR));
    }

    private void handle(HttpExchange exchange) throws IOException {
        method.set(exchange.getRequestMethod());
        path.set(exchange.getRequestURI().getPath());
        apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private HttpAiServiceClient client() {
        AsukaProperties.Ai ai = new AsukaProperties.Ai(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key",
                "master-token",
                "http://java:8080");
        AsukaProperties properties = new AsukaProperties(
                null, ai, null, null, null, null, null);
        return new HttpAiServiceClient(properties, new ObjectMapper());
    }
}
