package com.et.cloud.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async + scheduling infrastructure for the RAG pipeline. A dedicated small executor
 * keeps indexing load isolated from request threads; saturation degrades to
 * caller-runs instead of failing document operations.
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class RagAsyncConfig {

    @Bean("ragIndexExecutor")
    public Executor ragIndexExecutor(RagProperties ragProperties) {
        RagProperties.Index config = ragProperties.getIndex();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getAsyncCorePoolSize());
        executor.setMaxPoolSize(config.getAsyncMaxPoolSize());
        executor.setQueueCapacity(config.getAsyncQueueCapacity());
        executor.setThreadNamePrefix("rag-index-");
        executor.setRejectedExecutionHandler((r, e) -> {
            // queue full: log and drop, the daily reconciliation/backfill covers the gap
            log.warn("rag-index executor 队列已满，本次索引任务被跳过（回填/对账兜底）");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
