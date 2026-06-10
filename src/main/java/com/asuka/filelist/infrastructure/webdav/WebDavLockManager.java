package com.asuka.filelist.infrastructure.webdav;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebDAV 内存「假锁」：满足 Windows 资源管理器写入前的 LOCK/UNLOCK 流程，
 * 发放/登记 opaquelocktoken 并按超时清理，但不做强一致排他（首版以兼容为目标）。
 */
@Component
public class WebDavLockManager {

    private static final long DEFAULT_TIMEOUT_SECONDS = 3600;

    private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();

    /**
     * 对路径加锁，返回锁信息（含 token）。已存在未过期锁则续期返回原 token。
     */
    public Lock lock(String path) {
        long now = System.currentTimeMillis();
        return locks.compute(path, (key, existing) -> {
            if (existing != null && existing.expiresAtMillis > now) {
                return new Lock(existing.token, now + DEFAULT_TIMEOUT_SECONDS * 1000);
            }
            return new Lock("opaquelocktoken:" + UUID.randomUUID(), now + DEFAULT_TIMEOUT_SECONDS * 1000);
        });
    }

    /**
     * 释放锁；token 匹配且存在返回 true。
     */
    public boolean unlock(String path, String token) {
        Lock lock = locks.get(path);
        if (lock == null || !lock.token.equals(normalize(token))) {
            return false;
        }
        locks.remove(path);
        return true;
    }

    /**
     * 默认锁超时（秒）。
     */
    public long timeoutSeconds() {
        return DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * 去掉 Lock-Token 头的尖括号包裹。
     */
    private String normalize(String token) {
        if (token == null) {
            return "";
        }
        String t = token.trim();
        if (t.startsWith("<") && t.endsWith(">")) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    /**
     * 锁记录。
     */
    public record Lock(String token, long expiresAtMillis) {
    }
}
