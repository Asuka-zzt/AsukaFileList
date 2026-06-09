package com.asuka.filelist.application.task;

/**
 * 任务执行体的进度回报与取消检查回调。
 */
public interface TaskProgress {

    /**
     * 上报进度百分比（0-100），由实现做节流持久化。
     */
    void report(int percent);

    /**
     * 是否已被请求取消。
     */
    boolean isCanceled();

    /**
     * 若已取消则抛出 {@link TaskCanceledException} 中止执行。
     */
    void checkCanceled();
}
