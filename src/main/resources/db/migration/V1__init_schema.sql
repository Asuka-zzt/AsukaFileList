-- AsukaFileList 初始化表结构
-- 所有时间字段使用 DATETIME(3) 保留毫秒精度

-- ─────────────────────────────────────────────────────────────
-- 用户表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(64)  NOT NULL,
    -- 密码变更时间戳（毫秒），用于使该时间之前签发的 JWT 失效
    password_ts   BIGINT       NOT NULL DEFAULT 0,
    base_path     VARCHAR(500) NOT NULL DEFAULT '/',
    disabled      TINYINT(1)   NOT NULL DEFAULT 0,
    -- 权限位，与 AList 对齐（bit 0~16，详见 detailed-design.md §3.1）
    permission    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 角色表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE roles (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(500),
    -- JSON 数组，每项 {path, permission}，详见 detailed-design.md §3.2
    permission_scopes   TEXT,
    default_role        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 用户-角色关联表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 存储挂载表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE storages (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    mount_path       VARCHAR(500) NOT NULL,
    order_no         INT          NOT NULL DEFAULT 0,
    driver           VARCHAR(100) NOT NULL,
    cache_expiration INT          NOT NULL DEFAULT 30,
    -- work | disabled | init_error
    status           VARCHAR(20)  NOT NULL DEFAULT 'work',
    -- 驱动私有配置（JSON），各驱动自定义字段
    addition         TEXT,
    remark           VARCHAR(500),
    disabled         TINYINT(1)   NOT NULL DEFAULT 0,
    disable_index    TINYINT(1)   NOT NULL DEFAULT 0,
    enable_sign      TINYINT(1)   NOT NULL DEFAULT 0,
    -- name | size | modified
    order_by         VARCHAR(20)  NOT NULL DEFAULT 'name',
    -- asc | desc
    order_direction  VARCHAR(10)  NOT NULL DEFAULT 'asc',
    -- front | back | none
    extract_folder   VARCHAR(20)  NOT NULL DEFAULT 'front',
    web_proxy        TINYINT(1)   NOT NULL DEFAULT 0,
    -- proxy | redirect | native_proxy
    webdav_policy    VARCHAR(20)  NOT NULL DEFAULT 'proxy',
    proxy_range      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_storages_mount_path UNIQUE (mount_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 目录 Meta 规则表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE meta_rules (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    path          VARCHAR(500) NOT NULL,
    password      VARCHAR(255),
    -- 密码是否对子目录生效
    p_sub         TINYINT(1)   NOT NULL DEFAULT 0,
    write_enabled TINYINT(1)   NOT NULL DEFAULT 0,
    -- 写权限是否对子目录生效
    w_sub         TINYINT(1)   NOT NULL DEFAULT 0,
    -- 隐藏规则，每行一个正则表达式
    hide          TEXT,
    -- 隐藏规则是否对子目录生效
    h_sub         TINYINT(1)   NOT NULL DEFAULT 0,
    readme        TEXT,
    header        TEXT,
    PRIMARY KEY (id),
    CONSTRAINT uk_meta_path UNIQUE (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 分享表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE shares (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    share_id        VARCHAR(64)  NOT NULL,
    creator_id      BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL DEFAULT '',
    root_path       VARCHAR(500) NOT NULL,
    is_dir          TINYINT(1)   NOT NULL DEFAULT 0,
    password_hash   VARCHAR(255),
    password_salt   VARCHAR(64),
    burn_after_read TINYINT(1)   NOT NULL DEFAULT 0,
    -- 0 表示不限次数
    access_limit    BIGINT       NOT NULL DEFAULT 0,
    access_count    BIGINT       NOT NULL DEFAULT 0,
    allow_preview   TINYINT(1)   NOT NULL DEFAULT 1,
    allow_download  TINYINT(1)   NOT NULL DEFAULT 1,
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    expires_at      DATETIME,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_shares_share_id UNIQUE (share_id),
    CONSTRAINT fk_shares_creator FOREIGN KEY (creator_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 任务表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE tasks (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    -- upload | copy | build_index | ai_index
    type       VARCHAR(50) NOT NULL,
    -- PENDING | RUNNING | SUCCESS | FAILED | CANCELED | RETRYING
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress   INT         NOT NULL DEFAULT 0,
    creator_id BIGINT,
    payload    TEXT,
    result     TEXT,
    error      TEXT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_tasks_creator (creator_id),
    INDEX idx_tasks_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 文件名索引表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE file_index_nodes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    -- 父目录路径，根目录为空字符串
    parent     VARCHAR(500) NOT NULL DEFAULT '',
    name       VARCHAR(255) NOT NULL,
    is_dir     TINYINT(1)   NOT NULL DEFAULT 0,
    size       BIGINT       NOT NULL DEFAULT 0,
    storage_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_fin_parent (parent),
    INDEX idx_fin_storage (storage_id),
    INDEX idx_fin_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
