package com.asuka.filelist.infrastructure.persistence;

import com.asuka.filelist.infrastructure.persistence.entity.UserEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import com.asuka.filelist.infrastructure.persistence.handler.AuditMetaObjectHandler;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.*;

/**
 * UserMapper 基础 CRUD 和自动填充验证。
 * @Import 补充 AuditMetaObjectHandler，@MybatisPlusTest 切片不自动扫描普通 @Component。
 */
@MybatisPlusTest
@Import(AuditMetaObjectHandler.class)
@Sql("classpath:test-schema.sql")
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    // ─── 辅助方法 ─────────────────────────────────────────────

    private UserEntity newUser(String username) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setPasswordHash("hashed_password");
        u.setPasswordSalt("random_salt");
        return u;
    }

    // ─── 测试用例 ─────────────────────────────────────────────

    @Test
    void insert_andSelectByUsername() {
        userMapper.insert(newUser("admin"));

        UserEntity found = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, "admin"));

        assertThat(found).isNotNull();
        assertThat(found.getBasePath()).isEqualTo("/");
        assertThat(found.getDisabled()).isFalse();
        assertThat(found.getPermission()).isEqualTo(0);
    }

    @Test
    void autoFill_setsTimestamps() {
        UserEntity u = newUser("alice");
        userMapper.insert(u);

        // AuditMetaObjectHandler 应自动填充 createdAt / updatedAt
        assertThat(u.getCreatedAt()).isNotNull();
        assertThat(u.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRecord() {
        UserEntity u = newUser("bob");
        userMapper.insert(u);
        assertThat(userMapper.selectById(u.getId())).isNotNull();

        userMapper.deleteById(u.getId());
        assertThat(userMapper.selectById(u.getId())).isNull();
    }
}
