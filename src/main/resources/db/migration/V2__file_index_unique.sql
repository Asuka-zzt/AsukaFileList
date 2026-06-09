-- M6: 文件名索引唯一键，支持增量 upsert 与去重
-- (storage_id, parent, name) 唯一：同一存储同一父目录下文件名唯一
ALTER TABLE file_index_nodes
    ADD CONSTRAINT uk_fin_storage_parent_name UNIQUE (storage_id, parent, name);
