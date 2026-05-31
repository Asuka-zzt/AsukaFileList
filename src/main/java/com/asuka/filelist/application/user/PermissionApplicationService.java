package com.asuka.filelist.application.user;

import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.user.PermissionBits;
import com.asuka.filelist.domain.user.PermissionScope;
import com.asuka.filelist.domain.user.Role;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import org.springframework.stereotype.Service;

/**
 * 路径权限合并服务，为文件系统访问控制提供统一判定。
 */
@Service
public class PermissionApplicationService {

    /**
     * 合并用户基础权限和命中路径范围的角色权限。
     */
    public int resolvePermission(CurrentUser user, String requestPath) {
        String visiblePath = resolveUserVisiblePath(user, requestPath);
        boolean rootRequest = PathUtils.pathEquals(requestPath, "/")
                || PathUtils.pathEquals(visiblePath, user.basePath());
        ScopeMergeResult result = rootRequest ? mergeAllScopes(user) : mergeMatchedScopes(user, visiblePath);
        if (PermissionBits.has(user.permission(), PermissionBits.PATH_LIMIT) && !result.matched()) {
            return 0;
        }
        return user.permission() | result.permission();
    }

    /**
     * 判断当前用户在请求路径下是否具备指定权限。
     */
    public boolean hasPermission(CurrentUser user, String requestPath, int requiredMask) {
        return PermissionBits.has(resolvePermission(user, requestPath), requiredMask);
    }

    /**
     * 计算用户可见根路径下的实际访问路径。
     */
    public String resolveUserVisiblePath(CurrentUser user, String requestPath) {
        return PathUtils.joinBasePath(user.basePath(), requestPath);
    }

    /**
     * 合并全部角色范围权限，主要用于用户可见根。
     */
    private ScopeMergeResult mergeAllScopes(CurrentUser user) {
        int permission = 0;
        boolean matched = false;
        for (Role role : user.roles()) {
            for (PermissionScope scope : role.permissionScopes()) {
                permission |= scope.permission();
                matched = true;
            }
        }
        return new ScopeMergeResult(permission, matched);
    }

    /**
     * 合并命中访问路径的角色范围权限。
     */
    private ScopeMergeResult mergeMatchedScopes(CurrentUser user, String visiblePath) {
        int permission = 0;
        boolean matched = false;
        for (Role role : user.roles()) {
            for (PermissionScope scope : role.permissionScopes()) {
                if (PathUtils.isSubPath(scope.path(), visiblePath)) {
                    permission |= scope.permission();
                    matched = true;
                }
            }
        }
        return new ScopeMergeResult(permission, matched);
    }

    /**
     * 路径范围权限合并结果。
     */
    private record ScopeMergeResult(int permission, boolean matched) {
    }
}
