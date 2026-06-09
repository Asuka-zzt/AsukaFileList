package com.asuka.filelist.domain.task;

/**
 * 任务状态机枚举。
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED;

    /**
     * 是否为终态（不可再流转）。
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELED;
    }
}
