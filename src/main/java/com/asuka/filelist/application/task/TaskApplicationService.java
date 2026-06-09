package com.asuka.filelist.application.task;

import com.asuka.filelist.api.response.TaskPageResponse;
import com.asuka.filelist.api.response.TaskResponse;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.domain.task.TaskStatus;
import com.asuka.filelist.infrastructure.persistence.entity.TaskEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.TaskMapper;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务用例服务：查询/取消任务，统一所有权校验（本人或 admin）。
 */
@Service
public class TaskApplicationService {

    private final TaskMapper taskMapper;
    private final TaskExecutor taskExecutor;

    public TaskApplicationService(TaskMapper taskMapper, TaskExecutor taskExecutor) {
        this.taskMapper = taskMapper;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 分页查询当前用户任务，可按状态过滤，按创建时间倒序。
     */
    @Transactional(readOnly = true)
    public TaskPageResponse list(CurrentUser user, String status, int page, int perPage) {
        LambdaQueryWrapper<TaskEntity> query = new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getCreatorId, user.id())
                .orderByDesc(TaskEntity::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(TaskEntity::getStatus, status.trim().toUpperCase());
        }
        IPage<TaskEntity> result = taskMapper.selectPage(new Page<>(page, perPage), query);
        return new TaskPageResponse(
                result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(), page, perPage);
    }

    /**
     * 查询任务详情（所有权校验）。
     */
    @Transactional(readOnly = true)
    public TaskResponse get(CurrentUser user, Long id) {
        return toResponse(requireOwned(user, id));
    }

    /**
     * 取消任务：终态拒绝，否则置取消标志；执行线程负责写入 CANCELED 终态。
     */
    public void cancel(CurrentUser user, Long id) {
        TaskEntity entity = requireOwned(user, id);
        if (TaskStatus.valueOf(entity.getStatus()).isTerminal()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Task already finished");
        }
        if (!taskExecutor.requestCancel(id)) {
            // 无活跃执行线程（如重启后残留）：直接落库为 CANCELED
            TaskEntity update = new TaskEntity();
            update.setId(id);
            update.setStatus(TaskStatus.CANCELED.name());
            taskMapper.updateById(update);
        }
    }

    /**
     * 校验任务存在且属于当前用户（admin 可访问任意）。
     */
    private TaskEntity requireOwned(CurrentUser user, Long id) {
        TaskEntity entity = taskMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "Task not found");
        }
        if (!user.admin() && !user.id().equals(entity.getCreatorId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Permission denied");
        }
        return entity;
    }

    /**
     * 实体转响应。
     */
    private TaskResponse toResponse(TaskEntity entity) {
        return new TaskResponse(
                entity.getId(), entity.getType(), entity.getStatus(),
                entity.getProgress() == null ? 0 : entity.getProgress(),
                entity.getCreatorId(), entity.getResult(), entity.getError(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
