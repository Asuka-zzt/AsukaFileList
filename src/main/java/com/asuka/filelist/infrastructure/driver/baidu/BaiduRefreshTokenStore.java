package com.asuka.filelist.infrastructure.driver.baidu;

/**
 * 持久化百度轮换后的 refresh_token。
 *
 * <p>百度每次用 refresh_token 换 access_token 都会返回**新的** refresh_token 并作废旧的，
 * 因此必须把新值回写到存储配置，否则进程重启后会用已作废的 token 刷新而失败。
 */
public interface BaiduRefreshTokenStore {

    /**
     * 将指定存储 {@code addition} 中的 refreshToken 更新为最新值。
     */
    void update(long storageId, String newRefreshToken);
}
