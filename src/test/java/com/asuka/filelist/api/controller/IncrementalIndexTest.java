package com.asuka.filelist.api.controller;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 增量索引（写操作事件）与子树 update 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class IncrementalIndexTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 上传/删除/重命名通过异步事件增量维护索引，无需重建。
     */
    @Test
    void writeOps_updateIndexIncrementally(@TempDir Path tempDir) throws Exception {
        String admin = login("admin", "test-admin-password");
        createStorage(admin, "/m6inc", tempDir);

        upload(admin, "/m6inc/foxtrot.txt", "f");
        awaitContains(admin, "foxtrot", "/m6inc/foxtrot.txt");

        remove(admin, "/m6inc", "foxtrot.txt");
        awaitAbsent(admin, "foxtrot");

        mkdir(admin, "/m6inc/d1");
        upload(admin, "/m6inc/d1/hotel.txt", "h");
        awaitContains(admin, "hotel", "/m6inc/d1/hotel.txt");

        rename(admin, "/m6inc/d1/hotel.txt", "india.txt");
        awaitContains(admin, "india", "/m6inc/d1/india.txt");
        awaitAbsent(admin, "hotel");
    }

    /**
     * /api/admin/index/update 同步重建子树，能索引绕过 API 直接落盘的文件。
     */
    @Test
    void indexUpdate_reindexesSubtreeFromDisk(@TempDir Path tempDir) throws Exception {
        String admin = login("admin", "test-admin-password");
        createStorage(admin, "/m6upd", tempDir);

        // 直接写盘，不经 API => 无增量事件 => 暂不在索引
        Files.writeString(tempDir.resolve("juliet.txt"), "j");
        assertThat(searchPaths(admin, "juliet")).isEmpty();

        // 同步 update 子树后立即可搜
        mockMvc.perform(post("/api/admin/index/update").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/m6upd\"}"))
                .andExpect(status().isOk());
        assertThat(searchPaths(admin, "juliet")).containsExactly("/m6upd/juliet.txt");
    }

    private void awaitContains(String token, String keyword, String expectedPath) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (searchPaths(token, keyword).contains(expectedPath)) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(searchPaths(token, keyword)).contains(expectedPath);
    }

    private void awaitAbsent(String token, String keyword) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (searchPaths(token, keyword).isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(searchPaths(token, keyword)).isEmpty();
    }

    private List<String> searchPaths(String token, String keyword) throws Exception {
        String resp = mockMvc.perform(post("/api/fs/search").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"keyword\":\"" + keyword + "\",\"perPage\":200}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> paths = new ArrayList<>();
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

    private void remove(String token, String dir, String name) throws Exception {
        mockMvc.perform(post("/api/fs/remove").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dir\":\"" + dir + "\",\"names\":[\"" + name + "\"]}"))
                .andExpect(status().isOk());
    }

    private void rename(String token, String path, String name) throws Exception {
        mockMvc.perform(post("/api/fs/rename").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + path + "\",\"name\":\"" + name + "\"}"))
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
