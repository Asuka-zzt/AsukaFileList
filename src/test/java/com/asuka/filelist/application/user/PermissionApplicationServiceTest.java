package com.asuka.filelist.application.user;

import com.asuka.filelist.domain.user.PermissionBits;
import com.asuka.filelist.domain.user.PermissionScope;
import com.asuka.filelist.domain.user.Role;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PermissionApplicationService 路径权限合并测试。
 */
class PermissionApplicationServiceTest {

    private final PermissionApplicationService service = new PermissionApplicationService();

    /**
     * 命中角色路径范围时合并用户基础权限和角色权限。
     */
    @Test
    void resolvePermission_mergesUserAndMatchedRoleScopes() {
        CurrentUser user = user("/", PermissionBits.VIEW_HIDDEN, "/docs", PermissionBits.WRITE_UPLOAD);

        int permission = service.resolvePermission(user, "/docs/readme.md");

        assertThat(PermissionBits.has(permission, PermissionBits.VIEW_HIDDEN)).isTrue();
        assertThat(PermissionBits.has(permission, PermissionBits.WRITE_UPLOAD)).isTrue();
    }

    /**
     * 用户 basePath 会参与请求路径拼接。
     */
    @Test
    void resolveUserVisiblePath_joinsBasePath() {
        CurrentUser user = user("/home/alice", 0, "/home/alice/docs", PermissionBits.WEBDAV_READ);

        assertThat(service.resolveUserVisiblePath(user, "/docs")).isEqualTo("/home/alice/docs");
        assertThat(service.hasPermission(user, "/docs/file.txt", PermissionBits.WEBDAV_READ)).isTrue();
    }

    /**
     * 开启路径限制后，未命中任何 scope 的路径没有权限。
     */
    @Test
    void resolvePermission_withPathLimitRejectsUnmatchedPath() {
        CurrentUser user = user("/", PermissionBits.PATH_LIMIT, "/docs", PermissionBits.VIEW_HIDDEN);

        assertThat(service.resolvePermission(user, "/private")).isEqualTo(0);
        assertThat(service.hasPermission(user, "/docs", PermissionBits.VIEW_HIDDEN)).isTrue();
    }

    /**
     * 构造测试用户。
     */
    private CurrentUser user(String basePath, int userPermission, String scopePath, int scopePermission) {
        Role role = new Role(1L, "reader", "", List.of(new PermissionScope(scopePath, scopePermission)), false);
        return new CurrentUser(1L, "alice", basePath, userPermission, false, List.of(role));
    }
}
