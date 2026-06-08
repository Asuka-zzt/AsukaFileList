package com.asuka.filelist.application.meta;

import com.asuka.filelist.domain.meta.ResolvedMeta;
import com.asuka.filelist.infrastructure.persistence.entity.MetaRuleEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.MetaRuleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MetaApplicationService.resolve 就近匹配与 *Sub 继承单元测试。
 */
class MetaApplicationServiceTest {

    private final MetaRuleMapper metaRuleMapper = mock(MetaRuleMapper.class);
    private final MetaApplicationService service =
            new MetaApplicationService(metaRuleMapper, new MetaModelMapper());

    /**
     * 精确命中目录时，密码无条件生效。
     */
    @Test
    void resolve_exactMatchPasswordApplies() {
        stub(meta("/a/secret", "pwd", false, null, false));

        ResolvedMeta resolved = service.resolve("/a/secret");

        assertThat(resolved.hasPassword()).isTrue();
        assertThat(resolved.password()).isEqualTo("pwd");
    }

    /**
     * 祖先密码仅在 pSub=true 时对子目录生效。
     */
    @Test
    void resolve_ancestorPasswordRequiresPSub() {
        stub(meta("/a", "pwd", true, null, false));
        assertThat(service.resolve("/a/child").hasPassword()).isTrue();

        stub(meta("/a", "pwd", false, null, false));
        assertThat(service.resolve("/a/child").hasPassword()).isFalse();
    }

    /**
     * 多个祖先命中时取路径最长（最近）的规则。
     */
    @Test
    void resolve_picksNearestRule() {
        stub(
                meta("/a", "outer", true, null, false),
                meta("/a/b", "inner", true, null, false)
        );

        assertThat(service.resolve("/a/b/c").password()).isEqualTo("inner");
    }

    /**
     * 隐藏规则按 hSub 继承，README/Header 仅精确匹配生效。
     */
    @Test
    void resolve_hideInheritsButReadmeExactOnly() {
        MetaRuleEntity entity = meta("/a", null, false, "secret.*", true);
        entity.setReadme("hello readme");
        entity.setHeader("hi header");
        stub(entity);

        ResolvedMeta child = service.resolve("/a/b");
        assertThat(child.hideRegexes()).containsExactly("secret.*");
        assertThat(child.readme()).isEmpty();
        assertThat(child.header()).isEmpty();

        ResolvedMeta exact = service.resolve("/a");
        assertThat(exact.readme()).isEqualTo("hello readme");
        assertThat(exact.header()).isEqualTo("hi header");
    }

    /**
     * 无任何规则时返回空 Meta。
     */
    @Test
    void resolve_noRulesReturnsEmpty() {
        stub();
        ResolvedMeta resolved = service.resolve("/anything");
        assertThat(resolved.hasPassword()).isFalse();
        assertThat(resolved.hideRegexes()).isEmpty();
        assertThat(resolved.writeEnabled()).isFalse();
    }

    /**
     * 设置 selectList 返回的候选规则。
     */
    private void stub(MetaRuleEntity... entities) {
        when(metaRuleMapper.selectList(any())).thenReturn(List.of(entities));
    }

    /**
     * 构造测试 Meta 实体（password + hide 维度）。
     */
    private MetaRuleEntity meta(String path, String password, boolean pSub, String hide, boolean hSub) {
        MetaRuleEntity entity = new MetaRuleEntity();
        entity.setPath(path);
        entity.setPassword(password);
        entity.setPSub(pSub);
        entity.setHide(hide);
        entity.setHSub(hSub);
        entity.setWriteEnabled(false);
        entity.setWSub(false);
        return entity;
    }
}
