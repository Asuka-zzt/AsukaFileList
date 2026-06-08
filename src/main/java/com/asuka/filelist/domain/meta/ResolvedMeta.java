package com.asuka.filelist.domain.meta;

import java.util.Arrays;
import java.util.List;

/**
 * 某路径解析后的有效 Meta，各属性已按就近匹配与 *Sub 继承规则计算。
 * 任一属性可能为空，表示该路径上无对应规则生效。
 */
public record ResolvedMeta(
        String password,
        String hide,
        boolean writeEnabled,
        String readme,
        String header
) {

    /** 空 Meta，表示路径上无任何规则。 */
    public static ResolvedMeta empty() {
        return new ResolvedMeta("", "", false, "", "");
    }

    /** 是否设置了目录密码。 */
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    /** 将 hide 文本按行拆分为正则列表，忽略空行。 */
    public List<String> hideRegexes() {
        if (hide == null || hide.isBlank()) {
            return List.of();
        }
        return Arrays.stream(hide.split("\\r?\\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
