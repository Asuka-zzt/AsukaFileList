package com.asuka.filelist.domain.meta;

/**
 * 目录 Meta 规则领域模型，描述某挂载路径下的密码、写开关、隐藏规则与 README/Header。
 * 各 *Sub 标志表示该属性是否对子目录递归生效。
 */
public record MetaRule(
        Long id,
        String path,
        String password,
        boolean pSub,
        boolean writeEnabled,
        boolean wSub,
        String hide,
        boolean hSub,
        String readme,
        String header
) {
}
