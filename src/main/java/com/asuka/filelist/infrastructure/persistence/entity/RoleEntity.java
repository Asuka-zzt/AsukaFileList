package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 角色表，对应 roles */
@TableName("roles")
public class RoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** JSON 数组，每项 {path, permission}，详见 detailed-design.md §3.2 */
    private String permissionScopes;

    private Boolean defaultRole = false;

    // ─── Getters & Setters ───────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPermissionScopes() { return permissionScopes; }
    public void setPermissionScopes(String permissionScopes) { this.permissionScopes = permissionScopes; }

    public Boolean getDefaultRole() { return defaultRole; }
    public void setDefaultRole(Boolean defaultRole) { this.defaultRole = defaultRole; }
}
