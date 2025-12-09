package com.example.ratelimiter.infrastructure.notification;

import com.example.ratelimiter.application.dto.AlertNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logging-based alert notifier.
 *
 * This notifier is always enabled and logs alerts to the application logs.
 * It serves as a fallback notification mechanism when other channels are unavailable.
 *
 * Thread Safety: This class is thread-safe as SLF4J loggers are thread-safe.
 */
@Slf4j
@Component
public class LoggingNotifier implements AlertNotificationAdapter {

    @Override
    public void sendNotification(AlertNotification notification) {
        try {
            log.warn("ALERT TRIGGERED: {}", notification.alertRuleName());
            log.warn("Policy: {} (ID: {})", notification.policyName(), notification.policyId());
            log.warn("Current Deny Rate: {:.2f}% (Threshold: {}%)",
                notification.currentDenyRate(),
                notification.thresholdPercentage());
            log.warn("Window: {} seconds", notification.windowSeconds());
            log.warn("Total Requests: {}, Denied: {}",
                notification.totalRequests(),
                notification.deniedRequests());
            log.warn("Triggered At: {}", notification.triggeredAt());
        } catch (Exception e) {
            log.error("Failed to log alert notification: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isEnabled() {
        // Logging notifier is always enabled
        return true;
    }

    @Override
    public String getName() {
        return "Logging";
    }
}
