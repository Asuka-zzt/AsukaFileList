package com.asuka.filelist.api.controller;

import com.asuka.filelist.application.ai.AiKbTaskResponse;
import com.asuka.filelist.application.ai.AiServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3 知识库接口集成测试：CRUD、归属校验、文档去重、删除代理。
 * AI 服务以 {@link MockBean} 桩替换，仅验证 Java 侧编排与权限。
 */
@SpringBootTest
@AutoConfigureMockMvc
class KbControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiServiceClient aiServiceClient;

    /** 创建/列出/删除知识库，并验证跨用户越权按 404 处理。 */
    @Test
    void kbCrudAndOwnership() throws Exception {
        String admin = login("admin", "test-admin-password");

        long kbId = createKb(admin, "我的论文库", "测试");
        // workspace 回填为 kb_{id}
        listKbs(admin)
                .andExpect(jsonPath("$.data[0].id").value(kbId))
                .andExpect(jsonPath("$.data[0].workspace").value("kb_" + kbId))
                .andExpect(jsonPath("$.data[0].status").value("active"));

        // 另一个用户看不到、删不掉该库
        createUser(admin, "bob", "bob-password-123");
        String bob = login("bob", "bob-password-123");
        listKbs(bob).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(delete("/api/kb/" + kbId).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KB_NOT_FOUND"));
        // 不存在的库也 404
        mockMvc.perform(get("/api/kb/" + kbId + "/documents").header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());

        // 拥有者可删除，删除时清理 LightRAG workspace
        mockMvc.perform(delete("/api/kb/" + kbId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        verify(aiServiceClient).deleteKb(kbId);
        // 删除后该库不可再访问（不依赖全局计数，避免与其它用例共享 H2 时相互污染）
        mockMvc.perform(get("/api/kb/" + kbId + "/documents").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    /** 文档：加入触发索引、去重、列表、删除代理。 */
    @Test
    void documentLifecycleAndDedup(@TempDir Path tempDir) throws Exception {
        when(aiServiceClient.submitKbIndex(anyLong(), any()))
                .thenReturn(new AiKbTaskResponse("task-1", "pending", null));

        String admin = login("admin", "test-admin-password");
        createStorage(admin, "/kbtest", tempDir);
        upload(admin, "/kbtest/paper.pdf", "%PDF-1.4 dummy");

        long kbId = createKb(admin, "库A", null);

        // 加入文档 -> pending + taskId
        String addResp = addDocument(admin, kbId, "/kbtest/paper.pdf", "paper")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.fileName").value("paper.pdf"))
                .andReturn().getResponse().getContentAsString();
        long docId = dataId(addResp);
        verify(aiServiceClient).submitKbIndex(eq(kbId), any());

        // 重复加入同一文件 -> 409
        addDocument(admin, kbId, "/kbtest/paper.pdf", "paper")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KB_DOCUMENT_DUPLICATE"));

        // 文档列表
        mockMvc.perform(get("/api/kb/" + kbId + "/documents").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sourcePath").value("/kbtest/paper.pdf"));

        // 删除文档 -> 调 AI 删除该 doc 的索引
        mockMvc.perform(delete("/api/kb/" + kbId + "/documents/" + docId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        verify(aiServiceClient).deleteKbDocument(eq(kbId), anyString());
        mockMvc.perform(get("/api/kb/" + kbId + "/documents").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /** AI 索引状态回调按 docId 更新 kb_document；内部接口缺 master token 一律 401。 */
    @Test
    void indexStatusCallbackAndInternalAuth(@TempDir Path tempDir) throws Exception {
        when(aiServiceClient.submitKbIndex(anyLong(), any()))
                .thenReturn(new AiKbTaskResponse("task-9", "pending", null));
        String admin = login("admin", "test-admin-password");
        createStorage(admin, "/cb", tempDir);
        upload(admin, "/cb/p.pdf", "%PDF-1.4 dummy");
        long kbId = createKb(admin, "cbKB", null);
        String addResp = addDocument(admin, kbId, "/cb/p.pdf", "paper")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long docId = dataId(addResp);
        String lightragDocId = "kb" + kbId + "-doc" + docId;

        // 无 token -> 401
        mockMvc.perform(post("/internal/kb/index-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docId\":\"" + lightragDocId + "\",\"status\":\"indexed\"}"))
                .andExpect(status().isUnauthorized());

        // 正确 master token（测试配置 internal-download-token=test-token）-> 更新为 indexed
        mockMvc.perform(post("/internal/kb/index-callback")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docId\":\"" + lightragDocId
                                + "\",\"status\":\"indexed\",\"lightragDocId\":\"" + lightragDocId + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/kb/" + kbId + "/documents").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data[0].status").value("indexed"));

        // 内部下载缺 token -> 401
        mockMvc.perform(get("/internal/kb-download")
                        .param("path", "/cb/p.pdf").param("userId", "1").param("sign", "x"))
                .andExpect(status().isUnauthorized());
    }

    // ─── helpers ───────────────────────────────────────────────

    private long createKb(String token, String name, String description) throws Exception {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("name", name);
        if (description != null) {
            payload.put("description", description);
        }
        String body = objectMapper.writeValueAsString(payload);
        String resp = mockMvc.perform(post("/api/kb").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions listKbs(String token) throws Exception {
        return mockMvc.perform(get("/api/kb").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions addDocument(
            String token, long kbId, String path, String docType) throws Exception {
        String body = "{\"path\":\"" + path + "\",\"docType\":\"" + docType + "\"}";
        return mockMvc.perform(post("/api/kb/" + kbId + "/documents").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long dataId(String responseJson) {
        try {
            JsonNode node = objectMapper.readTree(responseJson);
            return node.path("data").path("id").asLong();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createUser(String adminToken, String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password
                + "\",\"basePath\":\"/\",\"permission\":0}";
        mockMvc.perform(post("/api/admin/user/create").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void upload(String token, String filePath, String content) throws Exception {
        mockMvc.perform(put("/api/fs/put").header("Authorization", "Bearer " + token)
                        .header("File-Path", filePath).header("Content-Type", "application/pdf")
                        .content(content.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    private void createStorage(String token, String mountPath, Path rootPath) throws Exception {
        String body = """
                {"mountPath":"%s","driver":"Local","addition":{"rootPath":"%s"},"disabled":false}
                """.formatted(mountPath, rootPath.toString().replace("\\", "\\\\"));
        mockMvc.perform(post("/api/admin/storage/create").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
