package com.asuka.filelist.infrastructure.event;

import com.asuka.filelist.application.search.FileNameIndexService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 增量索引监听器：异步消费文件变更事件，维护 file_index_nodes。
 * 索引为最终一致；异常仅记录不抛出，避免影响其他监听。
 */
@Component
public class FileIndexEventListener {

    private final FileNameIndexService fileNameIndexService;

    public FileIndexEventListener(FileNameIndexService fileNameIndexService) {
        this.fileNameIndexService = fileNameIndexService;
    }

    /**
     * 异步应用单条文件变更到索引。
     */
    @Async("asukaTaskExecutor")
    @EventListener
    public void onFileChange(FileChangeEvent event) {
        if (event.kind() == FileChangeEvent.Kind.UPSERT) {
            fileNameIndexService.upsertNode(event.storageId(), event.actualPath(), event.isDir(), event.size());
        } else {
            fileNameIndexService.removeNode(event.storageId(), event.actualPath());
        }
    }
}
