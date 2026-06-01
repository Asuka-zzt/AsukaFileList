package com.asuka.filelist.application.storage;

import com.asuka.filelist.api.response.StorageResponse;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.persistence.entity.StorageEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 存储实体、领域对象和响应对象转换器。
 */
@Component
public class StorageModelMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public StorageModelMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将持久化实体转换为领域对象。
     */
    public Storage toDomain(StorageEntity entity) {
        return new Storage(
                entity.getId(),
                entity.getMountPath(),
                entity.getOrderNo(),
                entity.getDriver(),
                entity.getCacheExpiration(),
                entity.getStatus(),
                entity.getAddition(),
                entity.getRemark(),
                null,
                Boolean.TRUE.equals(entity.getDisabled()),
                Boolean.TRUE.equals(entity.getDisableIndex()),
                Boolean.TRUE.equals(entity.getEnableSign()),
                entity.getOrderBy(),
                entity.getOrderDirection(),
                entity.getExtractFolder(),
                Boolean.TRUE.equals(entity.getWebProxy()),
                entity.getWebdavPolicy(),
                Boolean.TRUE.equals(entity.getProxyRange())
        );
    }

    /**
     * 将实体转换为 API 响应。
     */
    public StorageResponse toResponse(StorageEntity entity) {
        return new StorageResponse(
                entity.getId(),
                entity.getMountPath(),
                entity.getOrderNo(),
                entity.getDriver(),
                entity.getCacheExpiration(),
                entity.getStatus(),
                readAddition(entity.getAddition()),
                entity.getRemark(),
                Boolean.TRUE.equals(entity.getDisabled()),
                Boolean.TRUE.equals(entity.getDisableIndex()),
                Boolean.TRUE.equals(entity.getEnableSign()),
                entity.getOrderBy(),
                entity.getOrderDirection(),
                entity.getExtractFolder(),
                Boolean.TRUE.equals(entity.getWebProxy()),
                entity.getWebdavPolicy(),
                Boolean.TRUE.equals(entity.getProxyRange())
        );
    }

    /**
     * 将 addition 对象写为 JSON 字符串。
     */
    public String writeAddition(Map<String, Object> addition) {
        try {
            return objectMapper.writeValueAsString(addition == null ? Map.of() : addition);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid storage addition");
        }
    }

    /**
     * 从 JSON 字符串读取 addition 对象。
     */
    private Map<String, Object> readAddition(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid storage addition");
        }
    }
}
