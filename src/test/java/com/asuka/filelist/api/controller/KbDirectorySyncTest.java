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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库「整目录入库 + 增量同步」集成测试：异步批次、类型过滤、递归、增量 diff、归属。
 * AI 服务以 {@link MockBean} 桩替换，仅验证 Java 侧编排与权限。
 */
@SpringBootTest
@AutoConfigureMockMvc
class KbDirectorySyncTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiServiceClient aiServiceClient;

    /** 首次入库（递归 + 类型过滤）→ 未变同步 → 改动重建。 */
    @Test
    void directoryBatchThenIncrementalSync(@TempDir Path root) throws Exception {
        when(aiServiceClient.submitKbIndex(anyLong(), any()))
                .thenReturn(new AiKbTaskResponse("task-dir", "pending", null));

        // 直接在磁盘构造目录树：2 个根 md + 子目录 1 个 pdf（验证递归）+ 1 个不支持的 txt
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("a.md"), "# A");
        Files.writeString(root.resolve("b.md"), "# B");
        Files.writeString(root.resolve("sub/c.pdf"), "%PDF-1.4 c");
        Files.writeString(root.resolve("note.txt"), "skip me");

        String admin = login("admin", "test-admin-password");
        createStorage(admin, "/dir", root);
        long kbId = createKb(admin, "目录库", null);

        // 首次同步：3 个受支持入库，1 个不支持跳过
        JsonNode first = pollBatch(admin, kbId, syncDirectory(admin, kbId, "/dir"));
        assertThat(first.path("status").asText()).isEqualTo("completed");
        assertThat(first.path("total").asInt()).isEqualTo(3);
        assertThat(first.path("added").asInt()).isEqualTo(3);
        assertThat(first.path("skipped").asInt()).isEqualTo(1);
        assertThat(first.path("updated").asInt()).isEqualTo(0);
        assertThat(first.path("failed").asInt()).isEqualTo(0);
        verify(aiServiceClient, times(3)).submitKbIndex(anyLong(), any());
        mockMvc.perform(get("/api/kb/" + kbId + "/documents").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.length()").value(3));

        // 二次同步：无改动，全部 UNCHANGED，不再提交索引
        JsonNode second = pollBatch(admin, kbId, syncDirectory(admin, kbId, "/dir"));
        assertThat(second.path("unchanged").asInt()).isEqualTo(3);
        assertThat(second.path("added").asInt()).isEqualTo(0);
        assertThat(second.path("updated").asInt()).isEqualTo(0);
        verify(aiServiceClient, times(3)).submitKbIndex(anyLong(), any());

        // 改动 a.md（变更大小 + mtime）→ 仅它 MODIFIED 重建
        Files.writeString(root.resolve("a.md"), "# A changed with more content");
        Files.setLastModifiedTime(root.resolve("a.md"), FileTime.from(Instant.now().plusSeconds(5)));
        JsonNode third = pollBatch(admin, kbId, syncDirectory(admin, kbId, "/dir"));
        assertThat(third.path("updated").asInt()).isEqualTo(1);
        assertThat(third.path("unchanged").asInt()).isEqualTo(2);
        assertThat(third.path("added").asInt()).isEqualTo(0);
        // 改动文件重新提交一次索引（同 docId，旧索引由 AI 端幂等任务清理）
        verify(aiServiceClient, times(4)).submitKbIndex(anyLong(), any());
        // 文档数不变（复用同一行，不新建）
        mockMvc.perform(get("/api/kb/" + kbId + "/documents").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    /** 路径为文件而非目录 → 400；跨用户 KB → 404。 */
    @Test
    void directoryValidationAndOwnership(@TempDir Path root) throws Exception {
        when(aiServiceClient.submitKbIndex(anyLong(), any()))
                .thenReturn(new AiKbTaskResponse("t", "pending", null));
        Files.writeString(root.resolve("only.md"), "# only");

        String admin = login("admin", "test-admin-password");
        createStorage(admin, "/dir2", root);
        long kbId = createKb(admin, "校验库", null);

        // 文件而非目录
        mockMvc.perform(post("/api/kb/" + kbId + "/documents/directory")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/dir2/only.md\"}"))
                .andExpect(status().isBadRequest());

        // 跨用户越权（同步发起即拒绝）
        createUser(admin, "dirbob", "dirbob-password-123");
        String bob = login("dirbob", "dirbob-password-123");
        mockMvc.perform(post("/api/kb/" + kbId + "/documents/directory")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/dir2\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KB_NOT_FOUND"));
    }

    // ─── helpers ───────────────────────────────────────────────

    private long syncDirectory(String token, long kbId, String path) throws Exception {
        String resp = mockMvc.perform(post("/api/kb/" + kbId + "/documents/directory")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + path + "\",\"recursive\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    /** 轮询批次直到非 running（异步执行完成），超时即失败。 */
    private JsonNode pollBatch(String token, long kbId, long batchId) throws Exception {
        for (int i = 0; i < 100; i++) {
            String resp = mockMvc.perform(get("/api/kb/" + kbId + "/batches/" + batchId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode data = objectMapper.readTree(resp).path("data");
            if (!"running".equals(data.path("status").asText())) {
                return data;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("directory batch did not finish in time");
    }

    private long createKb(String token, String name, String description) throws Exception {
        String body = description == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}";
        String resp = mockMvc.perform(post("/api/kb").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private void createUser(String adminToken, String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password
                + "\",\"basePath\":\"/\",\"permission\":0}";
        mockMvc.perform(post("/api/admin/user/create").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
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
