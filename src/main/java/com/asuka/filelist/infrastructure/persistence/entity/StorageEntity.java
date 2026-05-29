package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 存储挂载表，对应 storages */
@TableName("storages")
public class StorageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mountPath;

    private Integer orderNo = 0;

    private String driver;

    private Integer cacheExpiration = 30;

    /** work | disabled | init_error */
    private String status = "work";

    /** 驱动私有配置（JSON） */
    private String addition;

    private String remark;

    private Boolean disabled = false;

    private Boolean disableIndex = false;

    private Boolean enableSign = false;

    /** name | size | modified */
    private String orderBy = "name";

    /** asc | desc */
    private String orderDirection = "asc";

    /** front | back | none */
    private String extractFolder = "front";

    private Boolean webProxy = false;

    /** proxy | redirect | native_proxy */
    private String webdavPolicy = "proxy";

    private Boolean proxyRange = false;

    // ─── Getters & Setters ───────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMountPath() { return mountPath; }
    public void setMountPath(String mountPath) { this.mountPath = mountPath; }

    public Integer getOrderNo() { return orderNo; }
    public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }

    public Integer getCacheExpiration() { return cacheExpiration; }
    public void setCacheExpiration(Integer cacheExpiration) { this.cacheExpiration = cacheExpiration; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAddition() { return addition; }
    public void setAddition(String addition) { this.addition = addition; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Boolean getDisableIndex() { return disableIndex; }
    public void setDisableIndex(Boolean disableIndex) { this.disableIndex = disableIndex; }

    public Boolean getEnableSign() { return enableSign; }
    public void setEnableSign(Boolean enableSign) { this.enableSign = enableSign; }

    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }

    public String getOrderDirection() { return orderDirection; }
    public void setOrderDirection(String orderDirection) { this.orderDirection = orderDirection; }

    public String getExtractFolder() { return extractFolder; }
    public void setExtractFolder(String extractFolder) { this.extractFolder = extractFolder; }

    public Boolean getWebProxy() { return webProxy; }
    public void setWebProxy(Boolean webProxy) { this.webProxy = webProxy; }

    public String getWebdavPolicy() { return webdavPolicy; }
    public void setWebdavPolicy(String webdavPolicy) { this.webdavPolicy = webdavPolicy; }

    public Boolean getProxyRange() { return proxyRange; }
    public void setProxyRange(Boolean proxyRange) { this.proxyRange = proxyRange; }
}
