package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** 用户表，对应 users */
@TableName("users")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String passwordSalt;

    /** 密码变更时间戳（毫秒），用于使旧 JWT 失效 */
    private Long passwordTs = 0L;

    private String basePath = "/";

    private Boolean disabled = false;

    /** 权限位，与 AList 对齐（bit 0~16），详见 detailed-design.md §3.1 */
    private Integer permission = 0;

    /** WebDAV Digest 凭据 HA1 = MD5(username:realm:webdavPassword)，null=未设置则禁用 WebDAV 登录 */
    private String webdavHa1;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ─── Getters & Setters ───────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }

    public Long getPasswordTs() { return passwordTs; }
    public void setPasswordTs(Long passwordTs) { this.passwordTs = passwordTs; }

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getPermission() { return permission; }
    public void setPermission(Integer permission) { this.permission = permission; }

    public String getWebdavHa1() { return webdavHa1; }
    public void setWebdavHa1(String webdavHa1) { this.webdavHa1 = webdavHa1; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
