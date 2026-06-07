package com.asuka.filelist.infrastructure.driver;

import java.io.InputStream;

/**
 * 上传文件流封装，供驱动写入使用。
 *
 * @param name        目标文件名（不含路径分隔符）
 * @param size        文件大小（字节），未知时为 -1
 * @param mimeType    内容类型，未知时为 null
 * @param inputStream 文件内容输入流，由调用方负责关闭
 */
public record UploadFile(
        String name,
        long size,
        String mimeType,
        InputStream inputStream
) {
}
