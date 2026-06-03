package com.asuka.filelist.application.user;

import com.asuka.filelist.api.request.AdminCreateRoleRequest;
import com.asuka.filelist.api.request.PermissionScopeRequest;
import com.asuka.filelist.api.response.RoleResponse;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.user.PermissionBits;
import com.asuka.filelist.domain.user.PermissionScope;
import com.asuka.filelist.domain.user.Role;
import com.asuka.filelist.infrastructure.persistence.entity.RoleEntity;
import com.asuka.filelist.infrastructure.persistence.entity.UserRoleEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.RoleMapper;
import com.asuka.filelist.infrastructure.persistence.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 角色用例服务，负责角色 CRUD 和权限范围 JSON 编解码。
 */
@Service
public class RoleApplicationService {

    private static final String ADMIN_ROLE = "admin";
    private static final TypeReference<List<PermissionScope>> SCOPE_LIST_TYPE = new TypeReference<>() {
    };

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final ObjectMapper objectMapper;

    public RoleApplicationService(RoleMapper roleMapper, UserRoleMapper userRoleMapper, ObjectMapper objectMapper) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询全部角色。
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoleResponses() {
        return roleMapper.selectList(null).stream()
                .map(this::toDomain)
                .map(this::toResponse)
                .toList();
    }

    /**
     * 管理员创建角色。
     */
    @Transactional
    public RoleResponse createRole(AdminCreateRoleRequest request) {
        List<PermissionScope> scopes = normalizeScopeRequests(request.permissionScopes());
        Role role = createRoleInternal(request.name(), request.description(), request.defaultRole(), scopes);
        return toResponse(role);
    }

    /**
     * 初始化流程按名称创建角色，已存在时直接返回。
     */
    @Transactional
    public Role createRoleIfAbsent(String name, String description, boolean defaultRole, List<PermissionScope> scopes) {
        Role existing = findByName(name);
        if (existing != null) {
            return existing;
        }
        return createRoleInternal(name, description, defaultRole, scopes);
    }

    /**
     * 查询用户绑定的角色。
     */
    @Transactional(readOnly = true)
    public List<Role> findRolesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream().map(this::toDomain).toList();
    }

    /**
     * 查询默认角色 ID，用于未指定角色的新用户。
     */
    @Transactional(readOnly = true)
    public List<Long> findDefaultRoleIds() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getDefaultRole, true))
                .stream()
                .map(RoleEntity::getId)
                .toList();
    }

    /**
     * 校验全部角色 ID 存在。
     */
    @Transactional(readOnly = true)
    public void ensureRolesExist(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        long count = roleMapper.selectBatchIds(roleIds).stream().map(RoleEntity::getId).distinct().count();
        if (count != roleIds.stream().distinct().count()) {
            throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND, "Role does not exist");
        }
    }

    /**
     * 判断角色集合是否包含管理员角色。
     */
    public boolean isAdmin(List<Role> roles) {
        return roles.stream().anyMatch(role -> ADMIN_ROLE.equals(role.name()));
    }

    /**
     * 将角色领域对象转为响应对象。
     */
    public RoleResponse toResponse(Role role) {
        return new RoleResponse(role.id(), role.name(), role.description(), role.defaultRole(), role.permissionScopes());
    }

    /**
     * 按名称查询角色。
     */
    @Transactional(readOnly = true)
    public Role findByName(String name) {
        RoleEntity entity = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getName, name));
        return entity == null ? null : toDomain(entity);
    }

    /**
     * 插入角色并返回领域对象。
     */
    private Role createRoleInternal(String name, String description, Boolean defaultRole, List<PermissionScope> scopes) {
        ensureNameUnused(name);
        RoleEntity entity = new RoleEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setDefaultRole(Boolean.TRUE.equals(defaultRole));
        entity.setPermissionScopes(writeScopes(scopes));
        roleMapper.insert(entity);
        return toDomain(entity);
    }

    /**
     * 校验角色名唯一。
     */
    private void ensureNameUnused(String name) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getName, name));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Role name already exists");
        }
    }

    /**
     * 规范化接口传入的权限范围。
     */
    private List<PermissionScope> normalizeScopeRequests(List<PermissionScopeRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .filter(Objects::nonNull)
                .map(item -> normalizeScope(item.path(), item.permission()))
                .toList();
    }

    /**
     * 规范化并校验单个权限范围。
     */
    private PermissionScope normalizeScope(String path, Integer permission) {
        int value = permission == null ? 0 : permission;
        if (!PermissionBits.validMask(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid permission mask");
        }
        return new PermissionScope(PathUtils.fixAndCleanPath(path), value);
    }

    /**
     * 将实体转换为领域模型。
     */
    private Role toDomain(RoleEntity entity) {
        return new Role(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                readScopes(entity.getPermissionScopes()),
                Boolean.TRUE.equals(entity.getDefaultRole())
        );
    }

    /**
     * 将权限范围序列化为 JSON。
     */
    private String writeScopes(List<PermissionScope> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes == null ? List.of() : scopes);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid permission scopes");
        }
    }

    /**
     * 从 JSON 读取角色权限范围。
     */
    private List<PermissionScope> readScopes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SCOPE_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid permission scopes");
        }
    }
}
