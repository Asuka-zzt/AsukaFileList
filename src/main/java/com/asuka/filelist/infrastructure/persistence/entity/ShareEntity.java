package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** 分享表，对应 shares */
@TableName("shares")
public class ShareEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公开链接标识，URL 安全的随机字符串 */
    private String shareId;

    private Long creatorId;

    private String name = "";

    private String rootPath;

    private Boolean isDir = false;

    private String passwordHash;

    private String passwordSalt;

    private Boolean burnAfterRead = false;

    /** 0 表示不限访问次数 */
    private Long accessLimit = 0L;

    private Long accessCount = 0L;

    private Boolean allowPreview = true;

    private Boolean allowDownload = true;

    private Boolean enabled = true;

    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ─── Getters & Setters ───────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShareId() { return shareId; }
    public void setShareId(String shareId) { this.shareId = shareId; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }

    public Boolean getIsDir() { return isDir; }
    public void setIsDir(Boolean dir) { isDir = dir; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }

    public Boolean getBurnAfterRead() { return burnAfterRead; }
    public void setBurnAfterRead(Boolean burnAfterRead) { this.burnAfterRead = burnAfterRead; }

    public Long getAccessLimit() { return accessLimit; }
    public void setAccessLimit(Long accessLimit) { this.accessLimit = accessLimit; }

    public Long getAccessCount() { return accessCount; }
    public void setAccessCount(Long accessCount) { this.accessCount = accessCount; }

    public Boolean getAllowPreview() { return allowPreview; }
    public void setAllowPreview(Boolean allowPreview) { this.allowPreview = allowPreview; }

    public Boolean getAllowDownload() { return allowDownload; }
    public void setAllowDownload(Boolean allowDownload) { this.allowDownload = allowDownload; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
