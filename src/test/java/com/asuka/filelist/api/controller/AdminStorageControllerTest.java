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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 存储管理和文件列表集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminStorageControllerTest {

    @TempDir
    private Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 管理员能创建 Local 存储，并通过 /api/fs/list 读取本地文件。
     */
    @Test
    void createLocalStorage_thenFsListReadsLocalDirectory() throws Exception {
        Files.writeString(tempDir.resolve("m3-file.txt"), "hello");
        String token = login();

        mockMvc.perform(get("/api/admin/driver/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(hasItems("Local", "S3", "BaiduNetdisk")));

        long storageId = createStorage(token, "/m3-local", tempDir);

        String listResponse = mockMvc.perform(post("/api/fs/list")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/m3-local\",\"perPage\":-1}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(listResponse).contains("m3-file.txt");

        disableStorage(token, storageId);
        mockMvc.perform(post("/api/fs/list")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/m3-local\",\"perPage\":-1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORAGE_NOT_FOUND"));
    }

    /**
     * 管理员登录并提取 token。
     */
    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    /**
     * 创建 Local 存储并返回 ID。
     */
    private long createStorage(String token, String mountPath, Path rootPath) throws Exception {
        String body = """
                {
                  "mountPath": "%s",
                  "driver": "Local",
                  "addition": {"rootPath": "%s"},
                  "disabled": false
                }
                """.formatted(mountPath, rootPath.toString().replace("\\", "\\\\"));
        String response = mockMvc.perform(post("/api/admin/storage/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("work"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    /**
     * 禁用指定存储。
     */
    private void disableStorage(String token, long storageId) throws Exception {
        mockMvc.perform(post("/api/admin/storage/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + storageId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("disabled"));
    }
}
