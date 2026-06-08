package com.asuka.filelist.infrastructure.driver;

import com.asuka.filelist.domain.fs.FileObject;

/**
 * 驱动写能力可选接口，由支持增删改和上传的驱动实现。
 * 不实现该接口的驱动视为只读，应用层应映射为 DRIVER_NOT_SUPPORTED。
 * 所有路径参数均为驱动内 actualPath，目录拷贝/移动仅限同一存储内执行。
 */
public interface DriverWriter {

    /**
     * 在 parentPath 目录下新建子目录，返回新目录对象。
     */
    FileObject mkdir(DriverContext context, String parentPath, String dirName);

    /**
     * 将 srcPath 移动到 dstDirPath 目录下，保持原文件名。
     */
    void move(DriverContext context, String srcPath, String dstDirPath);

    /**
     * 将 srcPath 复制到 dstDirPath 目录下，保持原文件名。
     */
    void copy(DriverContext context, String srcPath, String dstDirPath);

    /**
     * 将 srcPath 重命名为 newName（同目录内）。
     */
    void rename(DriverContext context, String srcPath, String newName);

    /**
     * 删除 path 指向的文件或目录（目录递归删除）。
     */
    void remove(DriverContext context, String path);

    /**
     * 将上传流写入 parentPath 目录，返回写入后的文件对象。
     */
    FileObject put(DriverContext context, String parentPath, UploadFile file);
}
