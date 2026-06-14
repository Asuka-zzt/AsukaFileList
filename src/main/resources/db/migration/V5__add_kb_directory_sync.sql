-- 知识库「整目录入库 + 增量同步」（2026-06-12）
-- 给文档加变更指纹列，并新增目录同步批次进度表。

-- 文档变更指纹：size + mtime，用于增量同步时判断「未变 / 改动」，无需读文件内容。
ALTER TABLE kb_document
    ADD COLUMN file_size       BIGINT      NULL AFTER task_id,
    ADD COLUMN source_modified DATETIME(3) NULL AFTER file_size;

-- 目录同步批次：一次「加入/同步整个目录」的进度与计数（异步执行，前端轮询）。
CREATE TABLE kb_directory_batch (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    kb_id       BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    source_path VARCHAR(1000) NOT NULL,
    -- running | completed | failed
    status      VARCHAR(20)   NOT NULL DEFAULT 'running',
    total       INT           NOT NULL DEFAULT 0,
    added       INT           NOT NULL DEFAULT 0,
    updated     INT           NOT NULL DEFAULT 0,
    unchanged   INT           NOT NULL DEFAULT 0,
    skipped     INT           NOT NULL DEFAULT 0,
    failed      INT           NOT NULL DEFAULT 0,
    error_msg   VARCHAR(2000),
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_kbbatch_kb (kb_id),
    KEY idx_kbbatch_user (user_id),
    CONSTRAINT fk_kbbatch_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
