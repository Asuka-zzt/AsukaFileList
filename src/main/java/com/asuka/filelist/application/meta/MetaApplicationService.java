package com.asuka.filelist.application.meta;

import com.asuka.filelist.api.request.MetaCreateRequest;
import com.asuka.filelist.api.request.MetaUpdateRequest;
import com.asuka.filelist.api.response.MetaRuleResponse;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.meta.MetaRule;
import com.asuka.filelist.domain.meta.ResolvedMeta;
import com.asuka.filelist.infrastructure.persistence.entity.MetaRuleEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.MetaRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 目录 Meta 用例服务：提供管理员 CRUD，以及文件系统读取时的就近规则解析。
 */
@Service
public class MetaApplicationService {

    private final MetaRuleMapper metaRuleMapper;
    private final MetaModelMapper modelMapper;

    public MetaApplicationService(MetaRuleMapper metaRuleMapper, MetaModelMapper modelMapper) {
        this.metaRuleMapper = metaRuleMapper;
        this.modelMapper = modelMapper;
    }

    /**
     * 查询全部 Meta 规则，按 path 升序。
     */
    @Transactional(readOnly = true)
    public List<MetaRuleResponse> list() {
        return metaRuleMapper.selectList(new LambdaQueryWrapper<MetaRuleEntity>()
                        .orderByAsc(MetaRuleEntity::getPath))
                .stream()
                .map(modelMapper::toResponse)
                .toList();
    }

    /**
     * 查询单个 Meta 规则。
     */
    @Transactional(readOnly = true)
    public MetaRuleResponse get(Long id) {
        return modelMapper.toResponse(requireEntity(id));
    }

    /**
     * 创建 Meta 规则。
     */
    @Transactional
    public MetaRuleResponse create(MetaCreateRequest request) {
        String path = PathUtils.fixAndCleanPath(request.path());
        validateHide(request.hide());
        ensurePathUnused(path, null);
        MetaRuleEntity entity = new MetaRuleEntity();
        entity.setPath(path);
        applyFields(entity, request.password(), request.pSub(), request.writeEnabled(),
                request.wSub(), request.hide(), request.hSub(), request.readme(), request.header());
        metaRuleMapper.insert(entity);
        return modelMapper.toResponse(metaRuleMapper.selectById(entity.getId()));
    }

    /**
     * 更新 Meta 规则。
     */
    @Transactional
    public MetaRuleResponse update(MetaUpdateRequest request) {
        MetaRuleEntity entity = requireEntity(request.id());
        String path = PathUtils.fixAndCleanPath(request.path());
        validateHide(request.hide());
        ensurePathUnused(path, request.id());
        entity.setPath(path);
        applyFields(entity, request.password(), request.pSub(), request.writeEnabled(),
                request.wSub(), request.hide(), request.hSub(), request.readme(), request.header());
        metaRuleMapper.updateById(entity);
        return modelMapper.toResponse(metaRuleMapper.selectById(request.id()));
    }

    /**
     * 删除 Meta 规则。
     */
    @Transactional
    public void delete(Long id) {
        requireEntity(id);
        metaRuleMapper.deleteById(id);
    }

    /**
     * 解析某内部路径上生效的有效 Meta。
     * 密码/隐藏/写开关按"自身命中或祖先命中且对应 *Sub=true"就近生效（路径最长者优先）；
     * README/Header 仅在精确匹配该目录时生效（schema 无独立 sub 标志）。
     */
    @Transactional(readOnly = true)
    public ResolvedMeta resolve(String internalPath) {
        String path = PathUtils.fixAndCleanPath(internalPath);
        // 仅取 path 为目标祖先-或-自身的规则作为候选
        List<MetaRule> candidates = metaRuleMapper.selectList(null).stream()
                .map(modelMapper::toDomain)
                .filter(meta -> PathUtils.isSubPath(meta.path(), path))
                .toList();

        String password = nearest(candidates, path, MetaRule::pSub, meta -> notBlank(meta.password()))
                .map(MetaRule::password).orElse("");
        String hide = nearest(candidates, path, MetaRule::hSub, meta -> notBlank(meta.hide()))
                .map(MetaRule::hide).orElse("");
        boolean writeEnabled = nearest(candidates, path, MetaRule::wSub, MetaRule::writeEnabled)
                .isPresent();

        Optional<MetaRule> exact = candidates.stream()
                .filter(meta -> PathUtils.pathEquals(meta.path(), path))
                .findFirst();
        String readme = exact.map(MetaRule::readme).filter(this::notBlank).orElse("");
        String header = exact.map(MetaRule::header).filter(this::notBlank).orElse("");

        return new ResolvedMeta(password, hide, writeEnabled, readme, header);
    }

    /**
     * 在候选中取就近生效的规则：精确命中无条件生效，祖先命中需 sub=true，最终取路径最长者。
     */
    private Optional<MetaRule> nearest(List<MetaRule> candidates, String path,
                                       Predicate<MetaRule> sub, Predicate<MetaRule> hasValue) {
        return candidates.stream()
                .filter(hasValue)
                .filter(meta -> PathUtils.pathEquals(meta.path(), path) || sub.test(meta))
                .max(Comparator.comparingInt(meta -> PathUtils.fixAndCleanPath(meta.path()).length()));
    }

    /**
     * 应用请求字段到实体，Boolean null 归一为 false。
     */
    private void applyFields(MetaRuleEntity entity, String password, Boolean pSub, Boolean writeEnabled,
                             Boolean wSub, String hide, Boolean hSub, String readme, String header) {
        entity.setPassword(password);
        entity.setPSub(Boolean.TRUE.equals(pSub));
        entity.setWriteEnabled(Boolean.TRUE.equals(writeEnabled));
        entity.setWSub(Boolean.TRUE.equals(wSub));
        entity.setHide(hide);
        entity.setHSub(Boolean.TRUE.equals(hSub));
        entity.setReadme(readme);
        entity.setHeader(header);
    }

    /**
     * 校验 hide 每行均为可编译正则。
     */
    private void validateHide(String hide) {
        if (hide == null || hide.isBlank()) {
            return;
        }
        for (String line : hide.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Pattern.compile(line.trim());
            } catch (PatternSyntaxException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid hide regex: " + line.trim());
            }
        }
    }

    /**
     * 校验 path 未被其他 Meta 占用。
     */
    private void ensurePathUnused(String path, Long selfId) {
        LambdaQueryWrapper<MetaRuleEntity> query = new LambdaQueryWrapper<MetaRuleEntity>()
                .eq(MetaRuleEntity::getPath, path);
        if (selfId != null) {
            query.ne(MetaRuleEntity::getId, selfId);
        }
        Long count = metaRuleMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Meta path already exists");
        }
    }

    /**
     * 查询 Meta 实体，不存在时抛异常。
     */
    private MetaRuleEntity requireEntity(Long id) {
        MetaRuleEntity entity = metaRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND, "Meta rule does not exist");
        }
        return entity;
    }

    /**
     * 非空白判断。
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
