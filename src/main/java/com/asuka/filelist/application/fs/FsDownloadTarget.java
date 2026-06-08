package com.asuka.filelist.application.fs;

import com.asuka.filelist.domain.fs.FileLink;
import com.asuka.filelist.domain.fs.FileObject;

/**
 * 下载解析结果，包含文件元数据与驱动生成的访问链接。
 */
public record FsDownloadTarget(
        FileObject file,
        FileLink link
) {
}
