package com.asuka.filelist.infrastructure.persistence.mapper;

import com.asuka.filelist.infrastructure.persistence.entity.FileIndexNodeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileIndexNodeMapper extends BaseMapper<FileIndexNodeEntity> {
}
