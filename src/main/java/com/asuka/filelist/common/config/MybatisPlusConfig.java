package com.asuka.filelist.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。注册分页内拦截器，供任务/搜索等 selectPage 使用。
 * 不指定 DbType，由连接自动探测方言（H2 测试 / MySQL 生产通用）。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
