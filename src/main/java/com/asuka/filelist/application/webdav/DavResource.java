package com.asuka.filelist.application.webdav;

import java.time.Instant;

/**
 * WebDAV 资源视图：从统一 VFS 的 FileObjectResponse 投影出 PROPFIND 所需字段。
 *
 * @param path      用户可见路径（如 /local/docs），WebDAV href 为 /dav + path
 * @param name      显示名（最后一段；根为空）
 * @param collection 是否目录（WebDAV collection）
 * @param size      字节大小（目录为 0）
 * @param modified  最后修改时间
 * @param created   创建时间
 */
public record DavResource(
        String path,
        String name,
        boolean collection,
        long size,
        Instant modified,
        Instant created
) {
}
