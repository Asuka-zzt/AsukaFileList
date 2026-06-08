package com.asuka.filelist.api.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4 文件读写闭环集成测试：mkdir/put/list/get/download(range)/rename/copy/move/remove。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FsReadWriteControllerTest {

    @TempDir
    private Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 走通一条完整读写闭环并校验本地文件系统副作用。
     */
    @Test
    void readWriteLoop_overLocalStorage() throws Exception {
        String token = login();
        createStorage(token, "/m4", tempDir);

        // mkdir /m4/docs
        mockMvc.perform(post("/api/fs/mkdir").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/m4/docs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDir").value(true));

        // upload /m4/docs/hello.txt
        mockMvc.perform(put("/api/fs/put").header("Authorization", "Bearer " + token)
                        .header("File-Path", "/m4/docs/hello.txt")
                        .content("hello world".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("hello.txt"));
        assertThat(Files.readString(tempDir.resolve("docs/hello.txt"))).isEqualTo("hello world");

        // get detail
        mockMvc.perform(post("/api/fs/get").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/m4/docs/hello.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("hello.txt"))
                .andExpect(jsonPath("$.data.size").value(11));

        // full download
        mockMvc.perform(get("/d/m4/docs/hello.txt").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("hello world"));

        // range download bytes=0-4 -> 206 "hello"
        mockMvc.perform(get("/d/m4/docs/hello.txt").header("Authorization", "Bearer " + token)
                        .header("Range", "bytes=0-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-4/11"))
                .andExpect(content().string("hello"));

        // rename hello.txt -> hi.txt
        mockMvc.perform(post("/api/fs/rename").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/m4/docs/hello.txt\",\"name\":\"hi.txt\"}"))
                .andExpect(status().isOk());
        assertThat(Files.exists(tempDir.resolve("docs/hi.txt"))).isTrue();

        // copy hi.txt into /m4
        mockMvc.perform(post("/api/fs/copy").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"srcDir\":\"/m4/docs\",\"dstDir\":\"/m4\",\"names\":[\"hi.txt\"]}"))
                .andExpect(status().isOk());
        assertThat(Files.exists(tempDir.resolve("hi.txt"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("docs/hi.txt"))).isTrue();

        // remove docs dir
        mockMvc.perform(post("/api/fs/remove").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dir\":\"/m4\",\"names\":[\"docs\"]}"))
                .andExpect(status().isOk());
        assertThat(Files.exists(tempDir.resolve("docs"))).isFalse();
    }

    /**
     * 写操作需 Bearer token，缺失时返回 401。
     */
    @Test
    void mkdir_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/fs/mkdir")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/m4/x\"}"))
                .andExpect(status().isUnauthorized());
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
     * 创建 Local 存储。
     */
    private void createStorage(String token, String mountPath, Path rootPath) throws Exception {
        String body = """
                {
                  "mountPath": "%s",
                  "driver": "Local",
                  "addition": {"rootPath": "%s"},
                  "disabled": false
                }
                """.formatted(mountPath, rootPath.toString().replace("\\", "\\\\"));
        mockMvc.perform(post("/api/admin/storage/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("work"));
    }
}
