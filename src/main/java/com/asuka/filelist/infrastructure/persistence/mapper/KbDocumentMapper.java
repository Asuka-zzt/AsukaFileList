package com.asuka.filelist.infrastructure.persistence.mapper;

import com.asuka.filelist.infrastructure.persistence.entity.KbDocumentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocumentEntity> {
}
