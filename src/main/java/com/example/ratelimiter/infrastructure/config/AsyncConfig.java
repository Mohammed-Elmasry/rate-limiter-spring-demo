package com.example.ratelimiter.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for asynchronous task execution.
 *
 * This configuration enables Spring's @Async support and provides a custom thread pool
 * optimized for metrics recording operations. The executor is tuned to handle high throughput
 * of rate limit event recording without blocking the main request processing.
 *
 * Thread Pool Design Considerations:
 * - Core pool size: 2 threads always running
 * - Max pool size: 10 threads to handle bursts
 * - Queue capacity: 500 tasks to buffer during high load
 * - Keep-alive time: 60 seconds for idle threads above core size
 * - Rejection policy: CallerRunsPolicy for backpressure (caller executes if queue full)
 *
 * The CallerRunsPolicy provides graceful degradation - if the async executor is saturated,
 * the recording will execute synchronously in the calling thread, providing natural backpressure
 * rather than dropping events.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * Custom thread pool executor for async metrics recording.
     * Named threads help with debugging and monitoring.
     *
     * @return Configured executor for async operations
     */
    @Bean(name = "metricsExecutor")
    public Executor metricsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - always running threads
        executor.setCorePoolSize(2);

        // Maximum pool size - threads created under load
        executor.setMaxPoolSize(10);

        // Queue capacity - buffer for pending tasks
        executor.setQueueCapacity(500);

        // Thread naming for easier debugging
        executor.setThreadNamePrefix("metrics-async-");

        // Keep alive time for idle threads above core size
        executor.setKeepAliveSeconds(60);

        // Allow core threads to time out when idle
        executor.setAllowCoreThreadTimeOut(false);

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Rejection policy - use CallerRunsPolicy for backpressure
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());

        executor.initialize();

        log.info("Initialized metrics executor with core={}, max={}, queue={}",
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Custom rejection handler that logs rejection and executes in caller thread.
     * This provides visibility into executor saturation while still processing the task.
     */
    private static class LoggingCallerRunsPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Metrics executor queue full - executing in caller thread. " +
                "Active: {}, Pool: {}, Queue: {}",
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size());

            // Execute in caller thread (provides backpressure)
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
}
