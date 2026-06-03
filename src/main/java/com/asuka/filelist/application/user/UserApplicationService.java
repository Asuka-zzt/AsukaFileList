package com.asuka.filelist.application.user;

import com.asuka.filelist.api.request.AdminCreateUserRequest;
import com.asuka.filelist.api.response.RoleResponse;
import com.asuka.filelist.api.response.UserResponse;
import com.asuka.filelist.application.auth.PasswordService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.user.Role;
import com.asuka.filelist.domain.user.User;
import com.asuka.filelist.infrastructure.persistence.entity.UserEntity;
import com.asuka.filelist.infrastructure.persistence.entity.UserRoleEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.UserMapper;
import com.asuka.filelist.infrastructure.persistence.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户用例服务，负责用户创建、查询和密码更新。
 */
@Service
public class UserApplicationService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleApplicationService roleApplicationService;
    private final PasswordService passwordService;

    public UserApplicationService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleApplicationService roleApplicationService,
            PasswordService passwordService
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleApplicationService = roleApplicationService;
        this.passwordService = passwordService;
    }

    /**
     * 查询全部用户。
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listUserResponses() {
        return userMapper.selectList(null).stream()
                .map(this::toDomain)
                .map(this::toResponse)
                .toList();
    }

    /**
     * 管理员创建用户。
     */
    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request) {
        List<Long> roleIds = resolveRoleIds(request.roleIds());
        User user = createUserInternal(
                request.username(),
                request.password(),
                request.basePath(),
                request.permission(),
                request.disabled(),
                roleIds
        );
        return toResponse(user);
    }

    /**
     * 初始化流程创建系统用户，已存在时直接返回。
     */
    @Transactional
    public User createSystemUser(
            String username,
            String password,
            String basePath,
            int permission,
            boolean disabled,
            List<Long> roleIds
    ) {
        User existing = findByUsername(username);
        if (existing != null) {
            return existing;
        }
        return createUserInternal(username, password, basePath, permission, disabled, roleIds);
    }

    /**
     * 按 ID 查询用户，不存在时抛业务异常。
     */
    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User does not exist");
        }
        return toDomain(entity);
    }

    /**
     * 按用户名查询用户。
     */
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        UserEntity entity = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        return entity == null ? null : toDomain(entity);
    }

    /**
     * 更新用户密码并刷新密码时间戳。
     */
    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND, "User does not exist");
        }
        entity.setPasswordHash(passwordService.hash(newPassword));
        entity.setPasswordSalt(passwordService.generateAuditSalt());
        entity.setPasswordTs(System.currentTimeMillis());
        userMapper.updateById(entity);
    }

    /**
     * 确保用户绑定指定角色。
     */
    @Transactional
    public void ensureRoleAssigned(Long userId, Long roleId) {
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId)
                .eq(UserRoleEntity::getRoleId, roleId));
        if (count != null && count > 0) {
            return;
        }
        UserRoleEntity relation = new UserRoleEntity();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        userRoleMapper.insert(relation);
    }

    /**
     * 将用户领域对象转为响应对象。
     */
    public UserResponse toResponse(User user) {
        List<RoleResponse> roles = user.roles().stream().map(roleApplicationService::toResponse).toList();
        return new UserResponse(
                user.id(),
                user.username(),
                user.basePath(),
                user.disabled(),
                user.permission(),
                roleApplicationService.isAdmin(user.roles()),
                roles
        );
    }

    /**
     * 插入用户和角色关系。
     */
    private User createUserInternal(
            String username,
            String password,
            String basePath,
            Integer permission,
            Boolean disabled,
            List<Long> roleIds
    ) {
        ensureUsernameUnused(username);
        roleApplicationService.ensureRolesExist(roleIds);
        UserEntity entity = buildUser(username, password, basePath, permission, disabled);
        userMapper.insert(entity);
        insertRoleRelations(entity.getId(), roleIds);
        return requireUser(entity.getId());
    }

    /**
     * 组装用户实体。
     */
    private UserEntity buildUser(String username, String password, String basePath, Integer permission, Boolean disabled) {
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordService.hash(password));
        entity.setPasswordSalt(passwordService.generateAuditSalt());
        entity.setPasswordTs(System.currentTimeMillis());
        entity.setBasePath(PathUtils.fixAndCleanPath(basePath));
        entity.setPermission(permission == null ? 0 : permission);
        entity.setDisabled(Boolean.TRUE.equals(disabled));
        return entity;
    }

    /**
     * 校验用户名唯一。
     */
    private void ensureUsernameUnused(String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username already exists");
        }
    }

    /**
     * 解析用户角色，未传入时使用默认角色。
     */
    private List<Long> resolveRoleIds(List<Long> requestedRoleIds) {
        if (requestedRoleIds != null && !requestedRoleIds.isEmpty()) {
            return requestedRoleIds.stream().distinct().toList();
        }
        return roleApplicationService.findDefaultRoleIds();
    }

    /**
     * 插入用户角色关联。
     */
    private void insertRoleRelations(Long userId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            UserRoleEntity relation = new UserRoleEntity();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    /**
     * 将实体转换为领域模型。
     */
    private User toDomain(UserEntity entity) {
        List<Role> roles = roleApplicationService.findRolesByUserId(entity.getId());
        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getPasswordSalt(),
                entity.getPasswordTs() == null ? 0L : entity.getPasswordTs(),
                entity.getBasePath(),
                Boolean.TRUE.equals(entity.getDisabled()),
                entity.getPermission() == null ? 0 : entity.getPermission(),
                roles
        );
    }
}
