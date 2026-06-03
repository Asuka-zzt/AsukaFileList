package com.asuka.filelist.domain.user;

/**
 * AList 兼容权限位定义，使用 bit mask 表达多个权限的组合。
 */
public final class PermissionBits {

    public static final int VIEW_HIDDEN = bit(0);
    public static final int BYPASS_PASSWORD = bit(1);
    public static final int OFFLINE_DOWNLOAD = bit(2);
    public static final int WRITE_UPLOAD = bit(3);
    public static final int RENAME = bit(4);
    public static final int MOVE = bit(5);
    public static final int COPY = bit(6);
    public static final int REMOVE = bit(7);
    public static final int WEBDAV_READ = bit(8);
    public static final int WEBDAV_WRITE = bit(9);
    public static final int FTP_READ = bit(10);
    public static final int FTP_WRITE = bit(11);
    public static final int ARCHIVE_READ = bit(12);
    public static final int EXTRACT = bit(13);
    public static final int PATH_LIMIT = bit(14);
    public static final int MCP_READ = bit(15);
    public static final int MCP_WRITE = bit(16);
    public static final int ALL = (1 << 17) - 1;

    private PermissionBits() {
    }

    /**
     * 将权限位序号转换为 mask。
     */
    public static int bit(int index) {
        return 1 << index;
    }

    /**
     * 判断权限集合是否包含指定 mask。
     */
    public static boolean has(int permission, int requiredMask) {
        return (permission & requiredMask) == requiredMask;
    }

    /**
     * 验证权限 mask 是否处于当前支持范围内。
     */
    public static boolean validMask(int permission) {
        return permission >= 0 && (permission | ALL) == ALL;
    }
}
