package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** 知识库文档表，对应 kb_document */
@TableName("kb_document")
public class KbDocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    /** 归属用户（冗余，便于越权校验与查询） */
    private Long userId;

    /** 源网盘文件路径（去重键） */
    private String sourcePath;

    /** 可选的稳定文件标识 */
    private Long sourceFileId;

    private String fileName;

    /** paper | book | note */
    private String docType = "paper";

    /** LightRAG 返回的 doc_id（索引成功回填） */
    private String lightragDocId;

    /** pending | parsing | indexing | indexed | failed */
    private String status = "pending";

    private String errorMsg;

    /** 关联的 AI 服务索引任务 id */
    private String taskId;

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

    public Long getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(Long sourceFileId) { this.sourceFileId = sourceFileId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getLightragDocId() { return lightragDocId; }
    public void setLightragDocId(String lightragDocId) { this.lightragDocId = lightragDocId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
