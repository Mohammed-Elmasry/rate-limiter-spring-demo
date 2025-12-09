package com.example.ratelimiter.application.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable record representing an alert notification.
 * Contains all necessary information about a triggered alert.
 */
@Builder
public record AlertNotification(
    UUID alertRuleId,
    String alertRuleName,
    UUID policyId,
    String policyName,
    double currentDenyRate,
    int thresholdPercentage,
    int windowSeconds,
    long totalRequests,
    long deniedRequests,
    OffsetDateTime triggeredAt
) {

    /**
     * Format the alert as a human-readable message.
     *
     * @return Formatted alert message
     */
    public String toMessage() {
        return String.format(
            "Alert: %s\n" +
            "Policy: %s (ID: %s)\n" +
            "Current Deny Rate: %.2f%%\n" +
            "Threshold: %d%%\n" +
            "Window: %d seconds\n" +
            "Total Requests: %d\n" +
            "Denied Requests: %d\n" +
            "Triggered At: %s",
            alertRuleName,
            policyName,
            policyId,
            currentDenyRate,
            thresholdPercentage,
            windowSeconds,
            totalRequests,
            deniedRequests,
            triggeredAt
        );
    }

    /**
     * Check if the alert should be triggered based on the threshold.
     *
     * @return true if current deny rate exceeds threshold
     */
    public boolean shouldTrigger() {
        return currentDenyRate >= thresholdPercentage;
    }
}
