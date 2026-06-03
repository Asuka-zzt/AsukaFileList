package com.asuka.filelist.infrastructure.persistence;

import com.asuka.filelist.infrastructure.persistence.entity.UserRoleEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserRoleMapper 关联表基础 CRUD 测试。
 */
@MybatisPlusTest
@Sql("classpath:test-schema.sql")
class UserRoleMapperTest {

    @Autowired
    private UserRoleMapper userRoleMapper;

    /**
     * 插入后可按 userId 查询角色关系。
     */
    @Test
    void insertAndSelectByUserId() {
        UserRoleEntity relation = new UserRoleEntity();
        relation.setUserId(1L);
        relation.setRoleId(2L);

        userRoleMapper.insert(relation);

        assertThat(userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, 1L)))
                .extracting(UserRoleEntity::getRoleId)
                .containsExactly(2L);
    }

    /**
     * 可以按 wrapper 删除指定用户角色关系。
     */
    @Test
    void deleteByWrapper_removesRelation() {
        UserRoleEntity relation = new UserRoleEntity();
        relation.setUserId(3L);
        relation.setRoleId(4L);
        userRoleMapper.insert(relation);

        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, 3L)
                .eq(UserRoleEntity::getRoleId, 4L));

        assertThat(userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, 3L))).isZero();
    }
}
