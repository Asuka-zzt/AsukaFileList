package com.asuka.filelist.common.util;

import java.net.URLConnection;

/**
 * 文件类型工具，按文件名推断 MIME 类型。
 */
public final class FileTypeUtils {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private FileTypeUtils() {
    }

    /**
     * 按文件名推断 Content-Type，未知时回退到二进制流。
     */
    public static String guessContentType(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        String type = URLConnection.guessContentTypeFromName(fileName);
        return type == null || type.isBlank() ? DEFAULT_CONTENT_TYPE : type;
    }
}
