package com.asuka.filelist.application.task;

import com.asuka.filelist.domain.task.TaskStatus;
import com.asuka.filelist.domain.task.TaskType;
import com.asuka.filelist.infrastructure.persistence.entity.TaskEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 任务执行器：持久化任务、异步调度执行体、维护状态机、节流进度、协作式取消。
 * 任务状态写入由本类统一负责，避免与外部并发改状态。
 */
@Component
public class TaskExecutor {

    private static final int ERROR_MAX = 2000;

    private final TaskMapper taskMapper;
    private final ThreadPoolTaskExecutor executor;
    // 活跃任务的取消标志：存在即表示任务已提交且未结束
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public TaskExecutor(TaskMapper taskMapper, @Qualifier("asukaTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.taskMapper = taskMapper;
        this.executor = executor;
    }

    /**
     * 提交任务：插入 PENDING 记录并异步执行，返回任务 id。
     * 非事务方法，插入即提交，保证异步线程能读到记录。
     */
    public Long submit(TaskType type, Long creatorId, String payload, Consumer<TaskProgress> work) {
        TaskEntity entity = new TaskEntity();
        entity.setType(type.code());
        entity.setStatus(TaskStatus.PENDING.name());
        entity.setProgress(0);
        entity.setCreatorId(creatorId);
        entity.setPayload(payload);
        taskMapper.insert(entity);
        Long id = entity.getId();
        cancelFlags.put(id, new AtomicBoolean(false));
        executor.execute(() -> run(id, work));
        return id;
    }

    /**
     * 请求取消：仅置标志，实际终态由执行线程写入。返回是否为活跃任务。
     */
    public boolean requestCancel(Long id) {
        AtomicBoolean flag = cancelFlags.get(id);
        if (flag == null) {
            return false;
        }
        flag.set(true);
        return true;
    }

    /**
     * 执行任务体并推进状态机。
     */
    private void run(Long id, Consumer<TaskProgress> work) {
        AtomicBoolean cancel = cancelFlags.get(id);
        try {
            if (cancel != null && cancel.get()) {
                updateStatus(id, TaskStatus.CANCELED, null, null);
                return;
            }
            updateStatus(id, TaskStatus.RUNNING, null, null);
            work.accept(new ThrottledProgress(id, cancel));
            updateStatus(id, TaskStatus.SUCCESS, 100, null);
        } catch (TaskCanceledException ex) {
            updateStatus(id, TaskStatus.CANCELED, null, null);
        } catch (Exception ex) {
            updateStatus(id, TaskStatus.FAILED, null, truncate(ex.getMessage()));
        } finally {
            cancelFlags.remove(id);
        }
    }

    /**
     * 写入任务状态（progress 为空则不改）。
     */
    private void updateStatus(Long id, TaskStatus status, Integer progress, String error) {
        TaskEntity update = new TaskEntity();
        update.setId(id);
        update.setStatus(status.name());
        update.setProgress(progress);
        update.setError(error);
        taskMapper.updateById(update);
    }

    /**
     * 写入任务进度。
     */
    private void updateProgress(Long id, int percent) {
        TaskEntity update = new TaskEntity();
        update.setId(id);
        update.setProgress(percent);
        taskMapper.updateById(update);
    }

    /**
     * 截断过长错误信息。
     */
    private String truncate(String message) {
        if (message == null) {
            return "Task failed";
        }
        return message.length() > ERROR_MAX ? message.substring(0, ERROR_MAX) : message;
    }

    /**
     * 节流进度实现：变化≥5% 或间隔≥2s 才落库，减少写压力。
     */
    private final class ThrottledProgress implements TaskProgress {

        private final Long id;
        private final AtomicBoolean cancel;
        private int lastPersisted = -1;
        private long lastTs = 0L;

        private ThrottledProgress(Long id, AtomicBoolean cancel) {
            this.id = id;
            this.cancel = cancel;
        }

        @Override
        public void report(int percent) {
            int clamped = Math.max(0, Math.min(100, percent));
            long now = System.currentTimeMillis();
            if (clamped - lastPersisted >= 5 || now - lastTs >= 2000L || clamped == 100) {
                updateProgress(id, clamped);
                lastPersisted = clamped;
                lastTs = now;
            }
        }

        @Override
        public boolean isCanceled() {
            return cancel != null && cancel.get();
        }

        @Override
        public void checkCanceled() {
            if (isCanceled()) {
                throw new TaskCanceledException();
            }
        }
    }
}
