package com.nkweb.pharma.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor used by the asynchronous export worker.
 *
 * <p>A bounded queue with a caller-runs rejection policy is used on purpose: if the export thread
 * pool is saturated the publishing (request) thread performs the drain itself instead of the work
 * being rejected. Combined with the database claim this is safe, and it guarantees that no
 * inquiry can ever be dropped silently.
 */
@Configuration
public class AsyncConfig {

    public static final String EXPORT_EXECUTOR = "inquiryExportExecutor";

    @Bean(name = EXPORT_EXECUTOR)
    public ThreadPoolTaskExecutor inquiryExportExecutor(AsyncProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.exportCorePoolSize());
        executor.setMaxPoolSize(properties.exportMaxPoolSize());
        executor.setQueueCapacity(properties.exportQueueCapacity());
        executor.setThreadNamePrefix("inquiry-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
