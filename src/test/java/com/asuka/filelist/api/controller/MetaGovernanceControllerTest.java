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
 * M5 目录治理集成测试：Meta CRUD、目录密码、隐藏过滤、下载签名。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetaGovernanceControllerTest {

    @TempDir
    private Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserApplicationService userApplicationService;

    /**
     * Meta 增删改查正常闭环。
     */
    @Test
    void metaCrud_byAdmin() throws Exception {
        String token = login("admin", "test-admin-password");

        // create
        String created = mockMvc.perform(post("/api/admin/meta/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/crud\",\"password\":\"p1\",\"readme\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("/crud"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").path("id").asLong();

        // get
        mockMvc.perform(get("/api/admin/meta/get").param("id", String.valueOf(id))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readme").value("hi"));

        // update
        mockMvc.perform(post("/api/admin/meta/update")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"path\":\"/crud\",\"password\":\"p2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").value("p2"));

        // delete
        mockMvc.perform(post("/api/admin/meta/delete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + "}"))
                .andExpect(status().isOk());
    }

    /**
     * 非法 hide 正则被拒绝。
     */
    @Test
    void metaCreate_rejectsInvalidHideRegex() throws Exception {
        String token = login("admin", "test-admin-password");
        mockMvc.perform(post("/api/admin/meta/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/badhide\",\"hide\":\"[unclosed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    /**
     * 普通用户访问 Meta 管理接口返回 403。
     */
    @Test
    void metaList_nonAdminForbidden() throws Exception {
        userApplicationService.createSystemUser("meta-guest", "guest-password",
                "/", PermissionBits.WRITE_UPLOAD, false, List.of());
        String token = login("meta-guest", "guest-password");
        mockMvc.perform(get("/api/admin/meta/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    /**
     * 目录密码、隐藏过滤与下载签名的端到端流程。
     */
    @Test
    void passwordHideAndSign_overLocalStorage() throws Exception {
        String admin = login("admin", "test-admin-password");
        createStorage(admin, "/m5", tempDir);
        mkdir(admin, "/m5/secret");
        upload(admin, "/m5/visible.txt", "v");
        upload(admin, "/m5/hidden.txt", "h");
        upload(admin, "/m5/secret/data.txt", "secret-data");

        // /m5 隐藏 hidden.txt；/m5/secret 设密码并对子项生效（pSub=true）
        createMeta(admin, "{\"path\":\"/m5\",\"hide\":\"hidden.txt\"}");
        createMeta(admin, "{\"path\":\"/m5/secret\",\"password\":\"pwd\",\"pSub\":true}");

        // 普通用户：无 VIEW_HIDDEN、无 BYPASS_PASSWORD
        userApplicationService.createSystemUser("m5user", "m5user-password",
                "/", PermissionBits.WRITE_UPLOAD, false, List.of());
        String user = login("m5user", "m5user-password");

        // 隐藏：普通用户看不到 hidden.txt，admin 能看到
        assertThat(listNames(user, "/m5", null)).contains("visible.txt", "secret").doesNotContain("hidden.txt");
        assertThat(listNames(admin, "/m5", null)).contains("hidden.txt");

        // 密码：缺失/错误/正确
        list(user, "/m5/secret", null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));
        list(user, "/m5/secret", "wrong").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_INCORRECT"));
        String correct = list(user, "/m5/secret", "pwd").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 下载签名：密码目录下文件无 sign 被拒，携带 list 下发的 sign 可下载
        String sign = signOf(correct, "data.txt");
        assertThat(sign).isNotBlank();
        mockMvc.perform(get("/d/m5/secret/data.txt").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/d/m5/secret/data.txt").param("sign", sign)
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk());
    }

    /**
     * 从 list 响应中取指定文件名的下载签名。
     */
    private String signOf(String listResponse, String name) throws Exception {
        for (JsonNode node : objectMapper.readTree(listResponse).path("data").path("content")) {
            if (name.equals(node.path("name").asText())) {
                return node.path("sign").asText();
            }
        }
        return "";
    }

    /**
     * 列目录并返回条目名集合。
     */
    private List<String> listNames(String token, String path, String password) throws Exception {
        String body = list(token, path, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("content")
                .findValuesAsText("name");
    }

    /**
     * 执行 /api/fs/list 请求。
     */
    private org.springframework.test.web.servlet.ResultActions list(String token, String path, String password) throws Exception {
        String pwd = password == null ? "" : ",\"password\":\"" + password + "\"";
        return mockMvc.perform(post("/api/fs/list").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"" + path + "\"" + pwd + ",\"perPage\":-1}"));
    }

    private void createMeta(String token, String json) throws Exception {
        mockMvc.perform(post("/api/admin/meta/create").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private void mkdir(String token, String path) throws Exception {
        mockMvc.perform(post("/api/fs/mkdir").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"" + path + "\"}"))
                .andExpect(status().isOk());
    }

    private void upload(String token, String filePath, String content) throws Exception {
        mockMvc.perform(put("/api/fs/put").header("Authorization", "Bearer " + token)
                        .header("File-Path", filePath)
                        .content(content.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private void createStorage(String token, String mountPath, Path rootPath) throws Exception {
        String body = """
                {"mountPath":"%s","driver":"Local","addition":{"rootPath":"%s"},"disabled":false}
                """.formatted(mountPath, rootPath.toString().replace("\\", "\\\\"));
        mockMvc.perform(post("/api/admin/storage/create").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
