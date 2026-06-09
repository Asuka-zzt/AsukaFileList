package com.asuka.filelist.application.task;

import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.domain.task.TaskType;
import com.asuka.filelist.infrastructure.persistence.entity.TaskEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 任务框架测试：状态流转、失败、协作式取消、所有权与控制器。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskFrameworkTest {

    @Autowired
    private TaskExecutor taskExecutor;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserApplicationService userApplicationService;

    /**
     * 正常任务流转到 SUCCESS，进度置 100。
     */
    @Test
    void task_runsToSuccess() throws Exception {
        Long id = taskExecutor.submit(TaskType.BUILD_INDEX, 1L, "{}", progress -> {
            progress.report(30);
            progress.report(100);
        });
        TaskEntity entity = awaitStatus(id, Set.of("SUCCESS"), 5000);
        assertThat(entity.getStatus()).isEqualTo("SUCCESS");
        assertThat(entity.getProgress()).isEqualTo(100);
    }

    /**
     * 任务体抛异常 -> FAILED，记录错误。
     */
    @Test
    void task_failureRecordsError() throws Exception {
        Long id = taskExecutor.submit(TaskType.BUILD_INDEX, 1L, "{}", progress -> {
            throw new IllegalStateException("boom");
        });
        TaskEntity entity = awaitStatus(id, Set.of("FAILED"), 5000);
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getError()).contains("boom");
    }

    /**
     * 运行中协作式取消 -> CANCELED。
     */
    @Test
    void task_cooperativeCancel() throws Exception {
        Long id = taskExecutor.submit(TaskType.BUILD_INDEX, 1L, "{}", progress -> {
            for (int i = 0; i < 100; i++) {
                progress.checkCanceled();
                progress.report(i);
                sleep(50);
            }
        });
        awaitStatus(id, Set.of("RUNNING"), 3000);
        taskExecutor.requestCancel(id);
        TaskEntity entity = awaitStatus(id, Set.of("CANCELED"), 5000);
        assertThat(entity.getStatus()).isEqualTo("CANCELED");
    }

    /**
     * 控制器：列表/详情可见；非所有者 403；已结束任务取消 400。
     */
    @Test
    void taskController_listGetOwnership() throws Exception {
        String token = login("admin", "test-admin-password");
        String me = mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        long adminId = objectMapper.readTree(me).path("data").path("id").asLong();

        Long taskId = taskExecutor.submit(TaskType.BUILD_INDEX, adminId, "{}", progress -> progress.report(100));
        awaitStatus(taskId, Set.of("SUCCESS"), 5000);

        mockMvc.perform(get("/api/task/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/task/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        userApplicationService.createSystemUser("task-other", "task-other-pass", "/", 0, false, List.of());
        String other = login("task-other", "task-other-pass");
        mockMvc.perform(get("/api/task/" + taskId).header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        mockMvc.perform(post("/api/task/" + taskId + "/cancel").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    /**
     * 轮询任务直到命中目标状态或超时。
     */
    private TaskEntity awaitStatus(Long id, Set<String> statuses, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            TaskEntity entity = taskMapper.selectById(id);
            if (entity != null && statuses.contains(entity.getStatus())) {
                return entity;
            }
            Thread.sleep(50);
        }
        return taskMapper.selectById(id);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
