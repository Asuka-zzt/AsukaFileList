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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M7 分享集成测试：公开浏览/下载、密码、过期、访问次数、阅后即焚、禁止下载、路径夹紧。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShareFlowTest {

    @TempDir
    private Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 无密码目录分享：info → auth → 公开 list/get → /sd 下载闭环，并校验路径夹在分享根内。
     */
    @Test
    void publicDirShare_browseAndDownload() throws Exception {
        String token = login();
        Path shared = Files.createDirectory(tempDir.resolve("shared"));
        Files.writeString(shared.resolve("inside.txt"), "shared-content");
        Files.writeString(tempDir.resolve("secret.txt"), "top-secret");
        createStorage(token, "/s1", tempDir);

        String shareId = createShare(token, "{\"rootPath\":\"/s1/shared\"}");

        // info：无需密码
        mockMvc.perform(get("/api/public/share/info").param("shareId", shareId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDir").value(true))
                .andExpect(jsonPath("$.data.needPassword").value(false));

        String shareToken = auth(shareId, null);

        // 列表：仅含分享根内条目，路径相对分享根；不泄漏 secret.txt
        mockMvc.perform(post("/api/public/share/list").header("X-Share-Token", shareToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":\"" + shareId + "\",\"subPath\":\"/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("inside.txt"))
                .andExpect(jsonPath("$.data.content[0].path").value("/inside.txt"))
                .andExpect(jsonPath("$.data.write").value(false));

        // 路径夹紧：subPath 试图用 .. 逃逸 → 仍夹在分享根，看不到 secret.txt
        String listEscape = mockMvc.perform(post("/api/public/share/list").header("X-Share-Token", shareToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":\"" + shareId + "\",\"subPath\":\"/../\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listEscape).doesNotContain("secret.txt");

        // 详情
        mockMvc.perform(post("/api/public/share/get").header("X-Share-Token", shareToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":\"" + shareId + "\",\"subPath\":\"/inside.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("inside.txt"));

        // 下载
        mockMvc.perform(get("/sd/" + shareId + "/inside.txt").param("token", shareToken))
                .andExpect(status().isOk())
                .andExpect(content().string("shared-content"));

        // 缺 token 的列表 → 401
        mockMvc.perform(post("/api/public/share/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":\"" + shareId + "\",\"subPath\":\"/\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 密码分享：info 标记需密码；错误密码 401；正确密码签发 token 后可访问。
     */
    @Test
    void passwordShare_requiresCorrectPassword() throws Exception {
        String token = login();
        Files.writeString(tempDir.resolve("p.txt"), "pw-data");
        createStorage(token, "/s2", tempDir);
        String shareId = createShare(token, "{\"rootPath\":\"/s2/p.txt\",\"password\":\"secret123\"}");

        mockMvc.perform(get("/api/public/share/info").param("shareId", shareId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDir").value(false))
                .andExpect(jsonPath("$.data.needPassword").value(true));

        mockMvc.perform(post("/api/public/share/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":\"" + shareId + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SHARE_PASSWORD_INCORRECT"));

        String shareToken = auth(shareId, "secret123");
        mockMvc.perform(get("/sd/" + shareId).param("token", shareToken))
                .andExpect(status().isOk())
                .andExpect(content().string("pw-data"));
    }

    /**
     * 过期分享：info 返回 410 SHARE_EXPIRED。
     */
    @Test
    void expiredShare_isGone() throws Exception {
        String token = login();
        Files.writeString(tempDir.resolve("e.txt"), "x");
        createStorage(token, "/s3", tempDir);
        String shareId = createShare(token, "{\"rootPath\":\"/s3/e.txt\",\"expiresAt\":\"2000-01-01T00:00:00\"}");

        mockMvc.perform(get("/api/public/share/info").param("shareId", shareId))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SHARE_EXPIRED"));
    }

    /**
     * 访问次数上限：limit=1，首次 auth 成功，二次 auth 视为过期。
     */
    @Test
    void accessLimit_blocksAfterLimit() throws Exception {
        String token = login();
        Files.writeString(tempDir.resolve("a.txt"), "x");
        createStorage(token, "/s4", tempDir);
        String shareId = createShare(token, "{\"rootPath\":\"/s4/a.txt\",\"accessLimit\":1}");

        auth(shareId, null);
        mockMvc.perform(post("/api/public/share/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":\"" + shareId + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SHARE_EXPIRED"));
    }

    /**
     * 阅后即焚：首次 auth 成功后分享失效，二次 auth 视为不存在。
     */
    @Test
    void burnAfterRead_disablesAfterFirstAuth() throws Exception {
        String token = login();
        Files.writeString(tempDir.resolve("b.txt"), "x");
        createStorage(token, "/s5", tempDir);
        String shareId = createShare(token, "{\"rootPath\":\"/s5/b.txt\",\"burnAfterRead\":true}");

        auth(shareId, null);
        mockMvc.perform(post("/api/public/share/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":\"" + shareId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_FOUND"));
    }

    /**
     * 禁止下载：可 auth/浏览，但 /sd 返回 403。
     */
    @Test
    void downloadDisabled_blocksSd() throws Exception {
        String token = login();
        Files.writeString(tempDir.resolve("d.txt"), "x");
        createStorage(token, "/s6", tempDir);
        String shareId = createShare(token, "{\"rootPath\":\"/s6/d.txt\",\"allowDownload\":false}");

        String shareToken = auth(shareId, null);
        mockMvc.perform(get("/sd/" + shareId).param("token", shareToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHARE_DOWNLOAD_DISABLED"));
    }

    /**
     * 删除不存在的分享返回 404（覆盖所有权/存在性判定分支）。
     */
    @Test
    void deleteMissingShare_returnsNotFound() throws Exception {
        String token = login();
        mockMvc.perform(post("/api/share/delete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_FOUND"));
    }

    // ─── helpers ───────────────────────────────────────────────

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private void createStorage(String token, String mountPath, Path rootPath) throws Exception {
        String body = """
                {"mountPath": "%s", "driver": "Local", "addition": {"rootPath": "%s"}, "disabled": false}
                """.formatted(mountPath, rootPath.toString().replace("\\", "\\\\"));
        mockMvc.perform(post("/api/admin/storage/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("work"));
    }

    private String createShare(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/api/share/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("shareId").asText();
    }

    private String auth(String shareId, String password) throws Exception {
        String body = password == null
                ? "{\"shareId\":\"" + shareId + "\"}"
                : "{\"shareId\":\"" + shareId + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/api/public/share/auth")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("token").asText();
    }
}
