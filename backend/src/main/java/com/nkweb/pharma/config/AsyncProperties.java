package com.nkweb.pharma.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sizing of the background executors.
 *
 * <p>The export executor is deliberately small: the goal is to take work off the request thread
 * without ever writing the same spreadsheet from more than a couple of threads. Correctness is
 * enforced by the file lock and the database claim, not by the pool size.
 *
 * @param exportCorePoolSize  baseline worker threads.
 * @param exportMaxPoolSize   upper bound of worker threads.
 * @param exportQueueCapacity bounded queue; a full queue triggers the caller-runs policy so the
 *                            sweeper can never silently drop work.
 */
@ConfigurationProperties(prefix = "app.async")
public record AsyncProperties(
        @DefaultValue("2") int exportCorePoolSize,
        @DefaultValue("4") int exportMaxPoolSize,
        @DefaultValue("500") int exportQueueCapacity) {

    public AsyncProperties {
        if (exportCorePoolSize < 1 || exportMaxPoolSize < exportCorePoolSize) {
            throw new IllegalArgumentException("app.async pool bounds are invalid");
        }
        if (exportQueueCapacity < 1) {
            throw new IllegalArgumentException("app.async.export-queue-capacity must be >= 1");
        }
    }
}
