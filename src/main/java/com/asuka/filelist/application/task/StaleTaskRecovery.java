package com.asuka.filelist.application.task;

import com.asuka.filelist.domain.task.TaskStatus;
import com.asuka.filelist.infrastructure.persistence.entity.TaskEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.TaskMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动期回收：进程内线程池不跨重启，将残留的 PENDING/RUNNING 任务标记为 FAILED(stale)。
 */
@Component
public class StaleTaskRecovery implements ApplicationRunner {

    private final TaskMapper taskMapper;

    public StaleTaskRecovery(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        LambdaUpdateWrapper<TaskEntity> update = new LambdaUpdateWrapper<TaskEntity>()
                .in(TaskEntity::getStatus, TaskStatus.PENDING.name(), TaskStatus.RUNNING.name())
                .set(TaskEntity::getStatus, TaskStatus.FAILED.name())
                .set(TaskEntity::getError, "Interrupted by server restart");
        taskMapper.update(null, update);
    }
}
