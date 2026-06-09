package com.asuka.filelist.api.controller;

import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.domain.user.PermissionBits;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 索引重建 + 文件名搜索集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskIndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserApplicationService userApplicationService;

    /**
     * 重建索引（异步任务）后可按文件名搜索；非 admin 不能重建。
     */
    @Test
    void buildIndexThenSearch(@TempDir Path tempDir) throws Exception {
        String admin = login("admin", "test-admin-password");
        long storageId = createStorage(admin, "/m6", tempDir);
        mkdir(admin, "/m6/sub");
        // 使用全局唯一 token，避免与其他测试类在共享 H2 索引中相互污染
        upload(admin, "/m6/qq6alpha.txt", "a");
        upload(admin, "/m6/sub/qq6beta.txt", "b");
        upload(admin, "/m6/gamma.log", "g");

        // 异步重建索引并等待完成
        long taskId = buildIndex(admin, storageId);
        awaitTaskSuccess(admin, taskId);

        // 搜索 qq6beta -> 命中 /m6/sub/qq6beta.txt
        assertThat(searchPaths(admin, "qq6beta")).containsExactly("/m6/sub/qq6beta.txt");

        // 搜索 qq6 -> alpha + beta（gamma.log 不含 token）
        assertThat(searchPaths(admin, "qq6")).containsExactlyInAnyOrder("/m6/qq6alpha.txt", "/m6/sub/qq6beta.txt");

        // 搜索不存在 -> 空
        assertThat(searchPaths(admin, "qq6none")).isEmpty();

        // 非 admin 重建 -> 403
        userApplicationService.createSystemUser("idx-guest", "idx-guest-pass", "/", PermissionBits.WRITE_UPLOAD, false, List.of());
        String guest = login("idx-guest", "idx-guest-pass");
        mockMvc.perform(post("/api/admin/index/build").header("Authorization", "Bearer " + guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    /**
     * basePath 受限用户搜索只看到自己根下文件。
     */
    @Test
    void search_respectsBasePathBoundary(@TempDir Path tempDir) throws Exception {
        String admin = login("admin", "test-admin-password");
        long storageId = createStorage(admin, "/m6b", tempDir);
        mkdir(admin, "/m6b/inner");
        upload(admin, "/m6b/ww6outer.txt", "o");
        upload(admin, "/m6b/inner/ww6treasure.txt", "t");
        awaitTaskSuccess(admin, buildIndex(admin, storageId));

        // 用户根锁定在 /m6b/inner
        userApplicationService.createSystemUser("scoped", "scoped-pass", "/m6b/inner", PermissionBits.WRITE_UPLOAD, false, List.of());
        String scoped = login("scoped", "scoped-pass");

        // 只能搜到 inner 下的 treasure，可见路径剥离 basePath；outer 在根外不可见
        assertThat(searchPaths(scoped, "ww6")).containsExactly("/ww6treasure.txt");
        assertThat(searchPaths(scoped, "ww6outer")).isEmpty();
    }

    private long buildIndex(String token, long storageId) throws Exception {
        String resp = mockMvc.perform(post("/api/admin/index/build").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"storageId\":" + storageId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").asLong();
    }

    private void awaitTaskSuccess(String token, long taskId) throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            String resp = mockMvc.perform(get("/api/task/" + taskId).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            String s = objectMapper.readTree(resp).path("data").path("status").asText();
            if ("SUCCESS".equals(s)) {
                return;
            }
            if ("FAILED".equals(s) || "CANCELED".equals(s)) {
                throw new AssertionError("index task ended " + s + ": " + resp);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("index task did not finish in time");
    }

    private List<String> searchPaths(String token, String keyword) throws Exception {
        String resp = mockMvc.perform(post("/api/fs/search").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"keyword\":\"" + keyword + "\",\"perPage\":200}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> paths = new java.util.ArrayList<>();
        for (JsonNode node : objectMapper.readTree(resp).path("data").path("content")) {
            paths.add(node.path("path").asText());
        }
        return paths;
    }

    private void mkdir(String token, String path) throws Exception {
        mockMvc.perform(post("/api/fs/mkdir").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"" + path + "\"}"))
                .andExpect(status().isOk());
    }

    private void upload(String token, String filePath, String content) throws Exception {
        mockMvc.perform(put("/api/fs/put").header("Authorization", "Bearer " + token)
                        .header("File-Path", filePath).header("Content-Type", "text/plain")
                        .content(content.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    private long createStorage(String token, String mountPath, Path rootPath) throws Exception {
        String body = """
                {"mountPath":"%s","driver":"Local","addition":{"rootPath":"%s"},"disabled":false}
                """.formatted(mountPath, rootPath.toString().replace("\\", "\\\\"));
        String resp = mockMvc.perform(post("/api/admin/storage/create").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
