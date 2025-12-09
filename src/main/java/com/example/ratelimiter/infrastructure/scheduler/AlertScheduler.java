package com.example.ratelimiter.infrastructure.scheduler;

import com.example.ratelimiter.application.service.AlertingService;
import com.example.ratelimiter.infrastructure.config.AlertingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to periodically check alert rules.
 *
 * This scheduler runs at a configurable interval (default: 60 seconds)
 * and checks all enabled alert rules against current metrics.
 *
 * Thread Safety: Spring's @Scheduled methods run in a thread pool managed by
 * Spring's TaskScheduler. By default, only one instance of a scheduled method
 * runs at a time. If a run takes longer than the interval, the next run waits
 * until the current one completes.
 *
 * Configuration:
 * - alerting.enabled=true (enables this scheduler)
 * - alerting.check-interval=60s (configures check frequency)
 *
 * Design Decisions:
 * - Fixed delay between runs to prevent overlap
 * - Conditional on alerting.enabled property
 * - Logs execution time for monitoring
 * - Catches all exceptions to prevent scheduler from stopping
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "alerting", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AlertScheduler {

    private final AlertingService alertingService;
    private final AlertingProperties alertingProperties;

    /**
     * Check all alert rules at a fixed rate.
     *
     * The schedule is configured via alerting.check-interval property.
     * Uses fixedDelayString to ensure minimum time between runs.
     *
     * Spring Expression Language (SpEL) is used to read the interval from
     * configuration properties, with a fallback to 60 seconds.
     */
    @Scheduled(
        fixedDelayString = "${alerting.check-interval:60s}",
        initialDelayString = "${alerting.initial-delay:30s}"
    )
    public void checkAlerts() {
        if (!alertingProperties.isEnabled()) {
            log.trace("Alerting is disabled, skipping check");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            log.debug("Starting alert rule check");
            alertingService.checkAlertRules();
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Alert rule check completed in {}ms", duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error during alert rule check (after {}ms): {}",
                duration, e.getMessage(), e);
            // Don't rethrow - we want the scheduler to continue running
        }
    }
}
