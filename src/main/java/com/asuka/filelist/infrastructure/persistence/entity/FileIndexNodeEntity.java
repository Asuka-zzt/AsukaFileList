package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

/** 文件名索引表，对应 file_index_nodes */
@TableName("file_index_nodes")
public class FileIndexNodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父目录路径，根目录为空字符串 */
    private String parent = "";

    private String name;

    private Boolean isDir = false;

    private Long size = 0L;

    private Long storageId;

    // ─── Getters & Setters ───────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getParent() { return parent; }
    public void setParent(String parent) { this.parent = parent; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getIsDir() { return isDir; }
    public void setIsDir(Boolean dir) { isDir = dir; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public Long getStorageId() { return storageId; }
    public void setStorageId(Long storageId) { this.storageId = storageId; }
}
