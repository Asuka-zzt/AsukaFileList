package com.asuka.filelist.infrastructure.persistence.mapper;

import com.asuka.filelist.infrastructure.persistence.entity.UserRoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联表 Mapper。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {
}
