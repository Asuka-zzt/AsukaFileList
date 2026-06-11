-- Graph RAG 知识库（P3）：知识库与文档的业务实体、归属、状态。
-- 向量/图/chunk 全部由 LightRAG 落 PostgreSQL，此处只存 MySQL 侧业务元数据。
-- 时间字段沿用 DATETIME(3) 毫秒精度。

-- ─────────────────────────────────────────────────────────────
-- 知识库表：一个用户可建多个 KB，一个 KB = 一个 LightRAG workspace（kb_{id}）
-- ─────────────────────────────────────────────────────────────
CREATE TABLE kb_knowledge_base (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    -- LightRAG workspace 名（kb_{id}），创建后由服务回填
    workspace   VARCHAR(64),
    -- active | deleting
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_kb_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 知识库文档表：来自网盘文件，记录解析/索引状态与 LightRAG doc_id
-- 去重：同一 KB 内按源文件路径唯一
-- ─────────────────────────────────────────────────────────────
CREATE TABLE kb_document (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    kb_id           BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    -- 源网盘文件路径（去重键）；source_file_id 为可选的稳定文件标识
    source_path     VARCHAR(1000) NOT NULL,
    source_file_id  BIGINT,
    file_name       VARCHAR(500) NOT NULL,
    -- paper | book | note
    doc_type        VARCHAR(20)  NOT NULL DEFAULT 'paper',
    -- LightRAG 返回的 doc_id（索引成功后回填）
    lightrag_doc_id VARCHAR(128),
    -- pending | parsing | indexing | indexed | failed
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
    error_msg       VARCHAR(2000),
    -- 关联的索引任务 id（AI 服务 Celery taskId）
    task_id         VARCHAR(128),
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_kbdoc_kb (kb_id),
    KEY idx_kbdoc_user (user_id),
    -- 去重：同 KB 内源路径唯一；source_path 较长，用 255 前缀以满足 InnoDB 索引键长限制
    UNIQUE KEY uk_kbdoc_kb_source (kb_id, source_path(255)),
    CONSTRAINT fk_kbdoc_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
