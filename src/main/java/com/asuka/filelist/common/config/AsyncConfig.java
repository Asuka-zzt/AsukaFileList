package com.asuka.filelist.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 进程内任务线程池配置。任务中心与增量索引监听共用 "taskExecutor"。
 * 参数固定为开发友好的小池；分布式队列待引入 Redis（需单独批准）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 任务执行线程池：核心 2 / 最大 4 / 队列 100，溢出由调用线程兜底执行。
     */
    @Bean("asukaTaskExecutor")
    public ThreadPoolTaskExecutor asukaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("asuka-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
