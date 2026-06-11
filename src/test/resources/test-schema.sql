-- H2 兼容版建表语句（供 @MybatisPlusTest 使用）
-- 去除 MySQL 专有语法：ENGINE、CHARSET、COLLATE、DATETIME(3)、ON UPDATE

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(64)  NOT NULL,
    password_ts   BIGINT       NOT NULL DEFAULT 0,
    base_path     VARCHAR(500) NOT NULL DEFAULT '/',
    disabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    permission    INT          NOT NULL DEFAULT 0,
    webdav_ha1    VARCHAR(64),
    created_at    DATETIME,
    updated_at    DATETIME
);

CREATE TABLE IF NOT EXISTS roles (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(100) NOT NULL UNIQUE,
    description       VARCHAR(500),
    permission_scopes CLOB,
    default_role      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS storages (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    mount_path       VARCHAR(500) NOT NULL UNIQUE,
    order_no         INT          NOT NULL DEFAULT 0,
    driver           VARCHAR(100) NOT NULL,
    cache_expiration INT          NOT NULL DEFAULT 30,
    status           VARCHAR(20)  NOT NULL DEFAULT 'work',
    addition         CLOB,
    remark           VARCHAR(500),
    disabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    disable_index    BOOLEAN      NOT NULL DEFAULT FALSE,
    enable_sign      BOOLEAN      NOT NULL DEFAULT FALSE,
    order_by         VARCHAR(20)  NOT NULL DEFAULT 'name',
    order_direction  VARCHAR(10)  NOT NULL DEFAULT 'asc',
    extract_folder   VARCHAR(20)  NOT NULL DEFAULT 'front',
    web_proxy        BOOLEAN      NOT NULL DEFAULT FALSE,
    webdav_policy    VARCHAR(20)  NOT NULL DEFAULT 'proxy',
    proxy_range      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS meta_rules (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    path          VARCHAR(500) NOT NULL UNIQUE,
    password      VARCHAR(255),
    p_sub         BOOLEAN      NOT NULL DEFAULT FALSE,
    write_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    w_sub         BOOLEAN      NOT NULL DEFAULT FALSE,
    hide          CLOB,
    h_sub         BOOLEAN      NOT NULL DEFAULT FALSE,
    readme        CLOB,
    header        CLOB
);

CREATE TABLE IF NOT EXISTS shares (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    share_id        VARCHAR(64)  NOT NULL UNIQUE,
    creator_id      BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL DEFAULT '',
    root_path       VARCHAR(500) NOT NULL,
    is_dir          BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash   VARCHAR(255),
    password_salt   VARCHAR(64),
    burn_after_read BOOLEAN      NOT NULL DEFAULT FALSE,
    access_limit    BIGINT       NOT NULL DEFAULT 0,
    access_count    BIGINT       NOT NULL DEFAULT 0,
    allow_preview   BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_download  BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at      DATETIME,
    created_at      DATETIME
);

CREATE TABLE IF NOT EXISTS tasks (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    type       VARCHAR(50) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress   INT         NOT NULL DEFAULT 0,
    creator_id BIGINT,
    payload    CLOB,
    result     CLOB,
    error      CLOB,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS file_index_nodes (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    parent     VARCHAR(500) NOT NULL DEFAULT '',
    name       VARCHAR(255) NOT NULL,
    is_dir     BOOLEAN      NOT NULL DEFAULT FALSE,
    size       BIGINT       NOT NULL DEFAULT 0,
    storage_id BIGINT       NOT NULL,
    CONSTRAINT uk_fin_storage_parent_name UNIQUE (storage_id, parent, name)
);

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    workspace   VARCHAR(64),
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at  DATETIME,
    updated_at  DATETIME
);

CREATE TABLE IF NOT EXISTS kb_document (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    kb_id           BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    source_path     VARCHAR(1000) NOT NULL,
    source_file_id  BIGINT,
    file_name       VARCHAR(500)  NOT NULL,
    doc_type        VARCHAR(20)   NOT NULL DEFAULT 'paper',
    lightrag_doc_id VARCHAR(128),
    status          VARCHAR(20)   NOT NULL DEFAULT 'pending',
    error_msg       VARCHAR(2000),
    task_id         VARCHAR(128),
    created_at      DATETIME,
    updated_at      DATETIME,
    CONSTRAINT uk_kbdoc_kb_source UNIQUE (kb_id, source_path)
);
