package com.example.ratelimiter.infrastructure.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Micrometer metrics and Prometheus export.
 *
 * This configuration provides:
 * - JVM metrics (memory, GC, threads, etc.)
 * - System metrics (CPU, uptime)
 * - Custom rate limiter metrics (via MetricsService)
 * - @Timed annotation support for method-level metrics
 *
 * Available metrics endpoints:
 * - /actuator/metrics - View all available metrics
 * - /actuator/prometheus - Prometheus-formatted metrics export
 *
 * Custom rate limiter metrics:
 * - rate_limiter.requests.allowed - Counter of allowed requests
 * - rate_limiter.requests.denied - Counter of denied requests
 * - rate_limiter.usage - Gauge of current usage per identifier
 * - rate_limiter.limit - Gauge of limit per identifier
 */
@Configuration
public class MetricsConfig {

    /**
     * Enable @Timed annotation support for method-level metrics.
     * This allows you to annotate methods with @Timed to automatically record execution time.
     *
     * Example:
     * {@code
     * @Timed(value = "rate.limit.check", description = "Time taken to check rate limit")
     * public RateLimitCheckResponse checkRateLimit(RateLimitCheckRequest request) {
     *     // method implementation
     * }
     * }
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Register JVM memory metrics (heap, non-heap, buffer pools).
     * These metrics help monitor application memory usage and potential memory leaks.
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * Register JVM GC metrics (pause duration, count, memory allocation).
     * These metrics help identify GC pressure and tuning opportunities.
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * Register JVM thread metrics (live threads, daemon threads, peak threads).
     * These metrics help monitor thread pool usage and potential deadlocks.
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * Register CPU metrics (usage, load average).
     * These metrics help monitor system resource utilization.
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     * Register uptime metrics (application start time, uptime).
     * These metrics help track application availability.
     */
    @Bean
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }
}
