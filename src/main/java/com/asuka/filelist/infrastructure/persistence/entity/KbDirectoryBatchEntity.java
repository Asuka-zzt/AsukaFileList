package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** 知识库目录入库批次表，对应 kb_directory_batch，记录一次目录同步的进度与计数。 */
@TableName("kb_directory_batch")
public class KbDirectoryBatchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    /** 归属用户（冗余，便于越权校验） */
    private Long userId;

    /** 同步的目录路径（用户可见路径） */
    private String sourcePath;

    /** running | completed | failed */
    private String status = "running";

    /** 展开的受支持文件总数 */
    private Integer total = 0;

    /** 新增文档数 */
    private Integer added = 0;

    /** 改动重建文档数 */
    private Integer updated = 0;

    /** 未变跳过数 */
    private Integer unchanged = 0;

    /** 不支持类型跳过数 */
    private Integer skipped = 0;

    /** 处理失败数 */
    private Integer failed = 0;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ─── Getters & Setters ───────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public Integer getAdded() { return added; }
    public void setAdded(Integer added) { this.added = added; }

    public Integer getUpdated() { return updated; }
    public void setUpdated(Integer updated) { this.updated = updated; }

    public Integer getUnchanged() { return unchanged; }
    public void setUnchanged(Integer unchanged) { this.unchanged = unchanged; }

    public Integer getSkipped() { return skipped; }
    public void setSkipped(Integer skipped) { this.skipped = skipped; }

    public Integer getFailed() { return failed; }
    public void setFailed(Integer failed) { this.failed = failed; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
