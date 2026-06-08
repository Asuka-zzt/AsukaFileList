package com.asuka.filelist.application.meta;

import com.asuka.filelist.api.response.MetaRuleResponse;
import com.asuka.filelist.domain.meta.MetaRule;
import com.asuka.filelist.infrastructure.persistence.entity.MetaRuleEntity;
import org.springframework.stereotype.Component;

/**
 * 目录 Meta 实体、领域对象与响应对象转换器。
 */
@Component
public class MetaModelMapper {

    /**
     * 将持久化实体转换为领域对象，Boolean null 归一为 false。
     */
    public MetaRule toDomain(MetaRuleEntity entity) {
        return new MetaRule(
                entity.getId(),
                entity.getPath(),
                entity.getPassword(),
                Boolean.TRUE.equals(entity.getPSub()),
                Boolean.TRUE.equals(entity.getWriteEnabled()),
                Boolean.TRUE.equals(entity.getWSub()),
                entity.getHide(),
                Boolean.TRUE.equals(entity.getHSub()),
                entity.getReadme(),
                entity.getHeader()
        );
    }

    /**
     * 将实体转换为 API 响应。
     */
    public MetaRuleResponse toResponse(MetaRuleEntity entity) {
        return new MetaRuleResponse(
                entity.getId(),
                entity.getPath(),
                entity.getPassword(),
                Boolean.TRUE.equals(entity.getPSub()),
                Boolean.TRUE.equals(entity.getWriteEnabled()),
                Boolean.TRUE.equals(entity.getWSub()),
                entity.getHide(),
                Boolean.TRUE.equals(entity.getHSub()),
                entity.getReadme(),
                entity.getHeader()
        );
    }
}
