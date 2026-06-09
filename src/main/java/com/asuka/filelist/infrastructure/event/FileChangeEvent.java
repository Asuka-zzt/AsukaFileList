package com.asuka.filelist.infrastructure.event;

/**
 * 文件变更事件，由 FsApplicationService 写操作发布，供增量索引等监听器异步消费。
 * actualPath 为存储内相对路径；isDir/size 仅 UPSERT 有意义。
 */
public record FileChangeEvent(
        Kind kind,
        Long storageId,
        String actualPath,
        boolean isDir,
        long size
) {

    public enum Kind {
        UPSERT,
        REMOVE
    }

    /**
     * 新建/更新一个节点。
     */
    public static FileChangeEvent upsert(Long storageId, String actualPath, boolean isDir, long size) {
        return new FileChangeEvent(Kind.UPSERT, storageId, actualPath, isDir, size);
    }

    /**
     * 删除一个节点（目录则连带子树）。
     */
    public static FileChangeEvent remove(Long storageId, String actualPath) {
        return new FileChangeEvent(Kind.REMOVE, storageId, actualPath, false, 0L);
    }
}
