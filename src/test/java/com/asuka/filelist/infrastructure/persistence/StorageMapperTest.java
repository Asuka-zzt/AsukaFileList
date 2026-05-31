package com.asuka.filelist.infrastructure.persistence;

import com.asuka.filelist.infrastructure.persistence.entity.StorageEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.StorageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * StorageMapper CRUD 与唯一约束验证。
 * @MybatisPlusTest 自动配置 H2 内存库；@Sql 执行 H2 兼容建表语句。
 */
@MybatisPlusTest
@Sql("classpath:test-schema.sql")
class StorageMapperTest {

    @Autowired
    private StorageMapper storageMapper;

    // ─── 辅助方法 ─────────────────────────────────────────────

    private StorageEntity newStorage(String mountPath) {
        StorageEntity e = new StorageEntity();
        e.setMountPath(mountPath);
        e.setDriver("Local");
        return e;
    }

    // ─── 测试用例 ─────────────────────────────────────────────

    @Test
    void insert_andSelectByMountPath() {
        storageMapper.insert(newStorage("/docs"));

        StorageEntity found = storageMapper.selectOne(
                new LambdaQueryWrapper<StorageEntity>()
                        .eq(StorageEntity::getMountPath, "/docs"));

        assertThat(found).isNotNull();
        assertThat(found.getDriver()).isEqualTo("Local");
        assertThat(found.getStatus()).isEqualTo("work");
        assertThat(found.getId()).isNotNull();
    }

    @Test
    void defaultValues_areApplied() {
        storageMapper.insert(newStorage("/data"));

        StorageEntity found = storageMapper.selectOne(
                new LambdaQueryWrapper<StorageEntity>()
                        .eq(StorageEntity::getMountPath, "/data"));

        assertThat(found.getOrderNo()).isEqualTo(0);
        assertThat(found.getCacheExpiration()).isEqualTo(30);
        assertThat(found.getDisabled()).isFalse();
        assertThat(found.getOrderBy()).isEqualTo("name");
        assertThat(found.getOrderDirection()).isEqualTo("asc");
    }

    @Test
    void selectEnabledStorages() {
        storageMapper.insert(newStorage("/active"));
        StorageEntity disabled = newStorage("/disabled");
        disabled.setDisabled(true);
        storageMapper.insert(disabled);

        List<StorageEntity> results = storageMapper.selectList(
                new LambdaQueryWrapper<StorageEntity>()
                        .eq(StorageEntity::getDisabled, false)
                        .orderByAsc(StorageEntity::getOrderNo));

        assertThat(results).extracting(StorageEntity::getMountPath)
                .containsOnly("/active");
    }

    @Test
    void updateById_changesFields() {
        StorageEntity e = newStorage("/update-test");
        storageMapper.insert(e);

        e.setStatus("disabled");
        e.setRemark("manually disabled");
        storageMapper.updateById(e);

        StorageEntity updated = storageMapper.selectById(e.getId());
        assertThat(updated.getStatus()).isEqualTo("disabled");
        assertThat(updated.getRemark()).isEqualTo("manually disabled");
    }
}
