package com.asuka.filelist.application.search;

import com.asuka.filelist.application.storage.MountedStorageRuntime;
import com.asuka.filelist.application.task.TaskProgress;
import com.asuka.filelist.domain.fs.BasicFileObject;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 存储文件树遍历器：从挂载根递归 driver.list，对每个条目回调 visitor。
 */
@Component
public class FileTreeWalker {

    /**
     * 递归遍历整个存储，对根以下每个文件/目录调用 visitor；progress 用于协作式取消。
     */
    public void walk(MountedStorageRuntime runtime, Consumer<FileObject> visitor, TaskProgress progress) {
        walkDir(runtime, rootObject(), visitor, progress);
    }

    /**
     * 递归遍历单个目录。
     */
    private void walkDir(MountedStorageRuntime runtime, FileObject dir, Consumer<FileObject> visitor, TaskProgress progress) {
        progress.checkCanceled();
        DriverContext context = new DriverContext(runtime.storage().mountPath(), Map.of());
        List<FileObject> children = runtime.driver().list(context, dir, new ListArgs(dir.path(), false));
        for (FileObject child : children) {
            visitor.accept(child);
            if (child.directory()) {
                walkDir(runtime, child, visitor, progress);
            }
        }
    }

    /**
     * 合成存储根对象（actualPath = "/"）。
     */
    private FileObject rootObject() {
        return new BasicFileObject("/", "/", "/", 0L, Instant.EPOCH, Instant.EPOCH, true, Map.of());
    }
}
