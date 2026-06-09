package com.asuka.filelist.infrastructure.driver.baidu;

import com.asuka.filelist.infrastructure.persistence.entity.StorageEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.StorageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把轮换后的百度 refresh_token 回写到 storages.addition（仅改 refreshToken 字段）。
 */
@Component
public class DbBaiduRefreshTokenStore implements BaiduRefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(DbBaiduRefreshTokenStore.class);

    private final StorageMapper storageMapper;
    private final ObjectMapper objectMapper;

    public DbBaiduRefreshTokenStore(StorageMapper storageMapper, ObjectMapper objectMapper) {
        this.storageMapper = storageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void update(long storageId, String newRefreshToken) {
        StorageEntity entity = storageMapper.selectById(storageId);
        if (entity == null || entity.getAddition() == null || entity.getAddition().isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(entity.getAddition());
            if (!(node instanceof ObjectNode addition)) {
                return;
            }
            addition.put("refreshToken", newRefreshToken);
            entity.setAddition(objectMapper.writeValueAsString(addition));
            storageMapper.updateById(entity);
        } catch (JsonProcessingException ex) {
            // 回写失败不应中断刷新；下次刷新会用内存中的最新值，仅重启后可能需要重配
            log.warn("Failed to persist rotated Baidu refresh_token for storage {}", storageId, ex);
        }
    }
}
