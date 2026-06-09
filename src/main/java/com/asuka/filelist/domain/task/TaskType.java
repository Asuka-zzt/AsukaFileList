package com.asuka.filelist.domain.task;

/**
 * 任务类型。M6 落地 BUILD_INDEX；UPLOAD/COPY 预留，待 M8 多驱动/跨存储就绪后实现。
 */
public enum TaskType {
    BUILD_INDEX("build_index"),
    UPLOAD("upload"),
    COPY("copy");

    private final String code;

    TaskType(String code) {
        this.code = code;
    }

    /**
     * 持久化到 tasks.type 的字符串编码。
     */
    public String code() {
        return code;
    }
}
