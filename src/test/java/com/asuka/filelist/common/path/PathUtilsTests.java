package com.asuka.filelist.common.path;

import com.asuka.filelist.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathUtilsTests {

    @Test
    void fixAndCleanPathNormalizesUnsafeSegments() {
        assertThat(PathUtils.fixAndCleanPath("/a//b/../c")).isEqualTo("/a/c");
        assertThat(PathUtils.fixAndCleanPath("a\\b")).isEqualTo("/a/b");
        assertThat(PathUtils.fixAndCleanPath(null)).isEqualTo("/");
    }

    @Test
    void joinBasePathKeepsRequestInsideUserBase() {
        assertThat(PathUtils.joinBasePath("/users/asuka", "/docs/readme.md"))
                .isEqualTo("/users/asuka/docs/readme.md");
        assertThat(PathUtils.joinBasePath("/", "/docs")).isEqualTo("/docs");
    }

    @Test
    void isSubPathMatchesWholePathSegments() {
        assertThat(PathUtils.isSubPath("/mnt", "/mnt/docs")).isTrue();
        assertThat(PathUtils.isSubPath("/mnt", "/mnt2/docs")).isFalse();
        assertThat(PathUtils.isSubPath("/", "/anything")).isTrue();
    }

    @Test
    void validateNameComponentRejectsPathSeparators() {
        assertThatThrownBy(() -> PathUtils.validateNameComponent("../x"))
                .isInstanceOf(BusinessException.class);
    }
}
