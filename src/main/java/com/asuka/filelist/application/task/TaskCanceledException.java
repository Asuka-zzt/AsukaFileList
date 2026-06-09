package com.asuka.filelist.application.task;

/**
 * 任务执行体在 {@link TaskProgress#checkCanceled()} 命中取消时抛出，由执行器转为 CANCELED 终态。
 */
public class TaskCanceledException extends RuntimeException {

    public TaskCanceledException() {
        super("Task canceled");
    }
}
