package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 目录 Meta 规则表，对应 meta_rules */
@TableName("meta_rules")
public class MetaRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String path;

    private String password;

    /** 密码是否对子目录生效 */
    private Boolean pSub = false;

    private Boolean writeEnabled = false;

    /** 写权限是否对子目录生效 */
    private Boolean wSub = false;

    /** 隐藏规则，每行一个正则表达式 */
    private String hide;

    /** 隐藏规则是否对子目录生效 */
    private Boolean hSub = false;

    private String readme;

    private String header;

    // ─── Getters & Setters ───────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Boolean getPSub() { return pSub; }
    public void setPSub(Boolean pSub) { this.pSub = pSub; }

    public Boolean getWriteEnabled() { return writeEnabled; }
    public void setWriteEnabled(Boolean writeEnabled) { this.writeEnabled = writeEnabled; }

    public Boolean getWSub() { return wSub; }
    public void setWSub(Boolean wSub) { this.wSub = wSub; }

    public String getHide() { return hide; }
    public void setHide(String hide) { this.hide = hide; }

    public Boolean getHSub() { return hSub; }
    public void setHSub(Boolean hSub) { this.hSub = hSub; }

    public String getReadme() { return readme; }
    public void setReadme(String readme) { this.readme = readme; }

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }
}
