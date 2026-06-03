package com.asuka.filelist.application.storage;

import com.asuka.filelist.api.request.StorageCreateRequest;
import com.asuka.filelist.api.request.StorageUpdateRequest;
import com.asuka.filelist.api.response.StorageResponse;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.StorageDriverRegistry;
import com.asuka.filelist.infrastructure.persistence.entity.StorageEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.StorageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 存储挂载管理用例服务。
 */
@Service
public class StorageApplicationService {

    private static final String STATUS_WORK = "work";
    private static final String STATUS_DISABLED = "disabled";

    private final StorageMapper storageMapper;
    private final StorageModelMapper modelMapper;
    private final StorageDriverRegistry driverRegistry;
    private final MountedStorageRegistry mountedStorageRegistry;

    public StorageApplicationService(
            StorageMapper storageMapper,
            StorageModelMapper modelMapper,
            StorageDriverRegistry driverRegistry,
            MountedStorageRegistry mountedStorageRegistry
    ) {
        this.storageMapper = storageMapper;
        this.modelMapper = modelMapper;
        this.driverRegistry = driverRegistry;
        this.mountedStorageRegistry = mountedStorageRegistry;
    }

    /**
     * 查询全部存储。
     */
    @Transactional(readOnly = true)
    public List<StorageResponse> list() {
        return storageMapper.selectList(new LambdaQueryWrapper<StorageEntity>()
                        .orderByAsc(StorageEntity::getOrderNo)
                        .orderByAsc(StorageEntity::getMountPath))
                .stream()
                .map(modelMapper::toResponse)
                .toList();
    }

    /**
     * 创建存储挂载。
     */
    @Transactional
    public StorageResponse create(StorageCreateRequest request) {
        String mountPath = normalizeMountPath(request.mountPath());
        ensureDriverExists(request.driver());
        ensureMountPathUnused(mountPath, null);
        StorageEntity entity = buildEntity(request, mountPath);
        storageMapper.insert(entity);
        refreshRuntime(entity);
        return modelMapper.toResponse(storageMapper.selectById(entity.getId()));
    }

    /**
     * 更新存储挂载。
     */
    @Transactional
    public StorageResponse update(StorageUpdateRequest request) {
        StorageEntity entity = requireEntity(request.id());
        String mountPath = normalizeMountPath(request.mountPath());
        ensureDriverExists(request.driver());
        ensureMountPathUnused(mountPath, request.id());
        applyUpdate(entity, request, mountPath);
        MountedStorageRuntime runtime = prepareRuntime(entity);
        storageMapper.updateById(entity);
        applyRuntime(entity, runtime);
        return modelMapper.toResponse(storageMapper.selectById(entity.getId()));
    }

    /**
     * 启用存储挂载。
     */
    @Transactional
    public StorageResponse enable(Long id) {
        StorageEntity entity = requireEntity(id);
        entity.setDisabled(false);
        entity.setStatus(STATUS_WORK);
        MountedStorageRuntime runtime = prepareRuntime(entity);
        storageMapper.updateById(entity);
        mountedStorageRegistry.replace(runtime);
        return modelMapper.toResponse(storageMapper.selectById(id));
    }

    /**
     * 禁用存储挂载。
     */
    @Transactional
    public StorageResponse disable(Long id) {
        StorageEntity entity = requireEntity(id);
        entity.setDisabled(true);
        entity.setStatus(STATUS_DISABLED);
        storageMapper.updateById(entity);
        mountedStorageRegistry.unmount(id);
        return modelMapper.toResponse(storageMapper.selectById(id));
    }

    /**
     * 删除存储挂载。
     */
    @Transactional
    public void delete(Long id) {
        requireEntity(id);
        storageMapper.deleteById(id);
        mountedStorageRegistry.unmount(id);
    }

    /**
     * 查询启用状态的存储领域对象。
     */
    @Transactional(readOnly = true)
    public List<Storage> enabledStorages() {
        return storageMapper.selectList(new LambdaQueryWrapper<StorageEntity>()
                        .eq(StorageEntity::getDisabled, false)
                        .orderByAsc(StorageEntity::getOrderNo))
                .stream()
                .map(modelMapper::toDomain)
                .toList();
    }

    /**
     * 更新指定存储的运行态状态。
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        StorageEntity entity = requireEntity(id);
        entity.setStatus(status);
        storageMapper.updateById(entity);
    }

    /**
     * 插入后刷新运行时。
     */
    private void refreshRuntime(StorageEntity entity) {
        if (Boolean.TRUE.equals(entity.getDisabled())) {
            entity.setStatus(STATUS_DISABLED);
            storageMapper.updateById(entity);
            return;
        }
        MountedStorageRuntime runtime = prepareRuntime(entity);
        entity.setStatus(STATUS_WORK);
        storageMapper.updateById(entity);
        mountedStorageRegistry.replace(runtime);
    }

    /**
     * 根据实体构造运行时，禁用存储返回 null。
     */
    private MountedStorageRuntime prepareRuntime(StorageEntity entity) {
        if (Boolean.TRUE.equals(entity.getDisabled())) {
            return null;
        }
        return mountedStorageRegistry.createRuntime(modelMapper.toDomain(entity));
    }

    /**
     * 根据实体状态应用运行时。
     */
    private void applyRuntime(StorageEntity entity, MountedStorageRuntime runtime) {
        if (Boolean.TRUE.equals(entity.getDisabled())) {
            mountedStorageRegistry.unmount(entity.getId());
        } else {
            mountedStorageRegistry.replace(runtime);
        }
    }

    /**
     * 创建存储实体。
     */
    private StorageEntity buildEntity(StorageCreateRequest request, String mountPath) {
        StorageEntity entity = new StorageEntity();
        entity.setMountPath(mountPath);
        entity.setDriver(request.driver());
        entity.setAddition(modelMapper.writeAddition(request.addition()));
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setRemark(request.remark());
        entity.setDisabled(Boolean.TRUE.equals(request.disabled()));
        entity.setStatus(Boolean.TRUE.equals(request.disabled()) ? STATUS_DISABLED : STATUS_WORK);
        return entity;
    }

    /**
     * 应用更新请求到实体。
     */
    private void applyUpdate(StorageEntity entity, StorageUpdateRequest request, String mountPath) {
        entity.setMountPath(mountPath);
        entity.setDriver(request.driver());
        entity.setAddition(modelMapper.writeAddition(request.addition()));
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setRemark(request.remark());
        entity.setDisabled(Boolean.TRUE.equals(request.disabled()));
        entity.setStatus(Boolean.TRUE.equals(request.disabled()) ? STATUS_DISABLED : STATUS_WORK);
    }

    /**
     * 查询存储实体，不存在时抛异常。
     */
    private StorageEntity requireEntity(Long id) {
        StorageEntity entity = storageMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND, "Storage does not exist");
        }
        return entity;
    }

    /**
     * 挂载路径规范化。
     */
    private String normalizeMountPath(String mountPath) {
        return PathUtils.fixAndCleanPath(mountPath);
    }

    /**
     * 校验驱动存在。
     */
    private void ensureDriverExists(String driver) {
        if (driverRegistry.findFactory(driver).isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Driver does not exist");
        }
    }

    /**
     * 校验挂载路径未被其他 storage 占用。
     */
    private void ensureMountPathUnused(String mountPath, Long selfId) {
        LambdaQueryWrapper<StorageEntity> query = new LambdaQueryWrapper<StorageEntity>()
                .eq(StorageEntity::getMountPath, mountPath);
        if (selfId != null) {
            query.ne(StorageEntity::getId, selfId);
        }
        Long count = storageMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Mount path already exists");
        }
    }
}
