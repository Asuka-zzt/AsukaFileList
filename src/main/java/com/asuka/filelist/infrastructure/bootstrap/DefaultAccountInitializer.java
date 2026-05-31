package com.asuka.filelist.infrastructure.bootstrap;

import com.asuka.filelist.application.user.RoleApplicationService;
import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.domain.user.PermissionBits;
import com.asuka.filelist.domain.user.PermissionScope;
import com.asuka.filelist.domain.user.Role;
import com.asuka.filelist.domain.user.User;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首次启动初始化基础角色和账号。
 */
@Component
public class DefaultAccountInitializer implements ApplicationRunner {

    private static final String ADMIN_ROLE = "admin";
    private static final String GUEST_ROLE = "guest";

    private final AsukaProperties properties;
    private final RoleApplicationService roleApplicationService;
    private final UserApplicationService userApplicationService;

    public DefaultAccountInitializer(
            AsukaProperties properties,
            RoleApplicationService roleApplicationService,
            UserApplicationService userApplicationService
    ) {
        this.properties = properties;
        this.roleApplicationService = roleApplicationService;
        this.userApplicationService = userApplicationService;
    }

    /**
     * 初始化 admin / guest 角色，并按配置创建系统账号。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AsukaProperties.Bootstrap bootstrap = properties.bootstrap();
        validateAdminPassword(bootstrap);
        Role adminRole = ensureAdminRole();
        Role guestRole = ensureGuestRole();
        User admin = userApplicationService.createSystemUser(
                bootstrap.adminUsername(),
                bootstrap.adminPassword(),
                "/",
                PermissionBits.ALL,
                false,
                List.of(adminRole.id())
        );
        userApplicationService.ensureRoleAssigned(admin.id(), adminRole.id());
        ensureGuestUserIfEnabled(bootstrap, guestRole);
    }

    /**
     * admin 初始密码为空时直接阻止启动。
     */
    private void validateAdminPassword(AsukaProperties.Bootstrap bootstrap) {
        if (bootstrap == null || bootstrap.adminPassword() == null || bootstrap.adminPassword().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ASUKA_ADMIN_PASSWORD must be configured");
        }
    }

    /**
     * 确保管理员角色存在。
     */
    private Role ensureAdminRole() {
        return roleApplicationService.createRoleIfAbsent(
                ADMIN_ROLE,
                "System administrator",
                false,
                List.of(new PermissionScope("/", PermissionBits.ALL))
        );
    }

    /**
     * 确保访客角色存在。
     */
    private Role ensureGuestRole() {
        return roleApplicationService.createRoleIfAbsent(
                GUEST_ROLE,
                "Guest user",
                true,
                List.of(new PermissionScope("/", 0))
        );
    }

    /**
     * 仅在显式启用访客账号时创建 guest 用户。
     */
    private void ensureGuestUserIfEnabled(AsukaProperties.Bootstrap bootstrap, Role guestRole) {
        if (!bootstrap.guestEnabled()) {
            return;
        }
        if (bootstrap.guestPassword() == null || bootstrap.guestPassword().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ASUKA_GUEST_PASSWORD must be configured");
        }
        userApplicationService.createSystemUser(
                bootstrap.guestUsername(),
                bootstrap.guestPassword(),
                "/",
                0,
                false,
                List.of(guestRole.id())
        );
    }
}
