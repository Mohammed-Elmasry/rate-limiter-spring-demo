package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.AlertNotification;
import com.example.ratelimiter.domain.entity.AlertRule;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.repository.AlertRuleRepository;
import com.example.ratelimiter.domain.repository.RateLimitEventRepository;
import com.example.ratelimiter.infrastructure.config.AlertingProperties;
import com.example.ratelimiter.infrastructure.notification.AlertNotificationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for monitoring metrics and triggering alerts.
 *
 * Key Responsibilities:
 * - Check alert rules against current metrics
 * - Determine if thresholds are exceeded
 * - Respect cooldown periods to prevent alert spam
 * - Dispatch notifications to configured channels
 * - Update alert rule state (lastTriggeredAt)
 *
 * Thread Safety: This service is called from scheduled tasks and must be thread-safe.
 * All operations are transactional, and the repository layer provides thread safety.
 *
 * Design Decisions:
 * - Non-blocking: Notification failures don't affect metric processing
 * - Cooldown enforcement: Prevents alert fatigue
 * - Multiple channels: Supports simultaneous notification to all enabled channels
 * - Transactional updates: Ensures lastTriggeredAt is persisted correctly
 */
@Slf4j
@Service
public class AlertingService {

    private final AlertRuleRepository alertRuleRepository;
    private final RateLimitEventRepository eventRepository;
    private final AlertingProperties alertingProperties;
    private final List<AlertNotificationAdapter> notifiers;

    public AlertingService(
        AlertRuleRepository alertRuleRepository,
        RateLimitEventRepository eventRepository,
        AlertingProperties alertingProperties,
        List<AlertNotificationAdapter> notifiers
    ) {
        this.alertRuleRepository = alertRuleRepository;
        this.eventRepository = eventRepository;
        this.alertingProperties = alertingProperties;
        this.notifiers = notifiers;

        log.info("Alerting service initialized with {} notifiers: {}",
            notifiers.size(),
            notifiers.stream().map(AlertNotificationAdapter::getName).toList());
    }

    /**
     * Check all enabled alert rules and trigger notifications if thresholds are exceeded.
     *
     * This method is called by the scheduler at regular intervals.
     * It processes all enabled alert rules in a single transaction.
     */
    @Transactional
    public void checkAlertRules() {
        if (!alertingProperties.isEnabled()) {
            log.debug("Alerting system is disabled");
            return;
        }

        List<AlertRule> enabledRules = alertRuleRepository.findByEnabledTrue();
        log.debug("Checking {} enabled alert rules", enabledRules.size());

        for (AlertRule rule : enabledRules) {
            try {
                checkAndTriggerAlert(rule);
            } catch (Exception e) {
                log.error("Error checking alert rule {}: {}", rule.getId(), e.getMessage(), e);
                // Continue processing other rules even if one fails
            }
        }
    }

    /**
     * Check a specific alert rule and trigger notification if threshold is exceeded.
     *
     * @param rule The alert rule to check
     */
    @Transactional
    public void checkAndTriggerAlert(AlertRule rule) {
        log.debug("Checking alert rule: {} (ID: {})", rule.getName(), rule.getId());

        // Skip if policy is not set (global rules not yet supported)
        if (rule.getPolicy() == null) {
            log.debug("Skipping alert rule {} - no policy associated", rule.getId());
            return;
        }

        // Check if in cooldown period
        if (isInCooldown(rule)) {
            log.debug("Alert rule {} is in cooldown period", rule.getId());
            return;
        }

        // Calculate deny rate for the specified time window
        DenyRateMetrics metrics = calculateDenyRate(rule);

        // Check if threshold is exceeded
        if (metrics.denyRate() >= rule.getThresholdPercentage()) {
            log.info("Alert threshold exceeded for rule {}: {:.2f}% >= {}%",
                rule.getName(), metrics.denyRate(), rule.getThresholdPercentage());

            triggerAlert(rule, metrics);
        } else {
            log.debug("Alert rule {} within threshold: {:.2f}% < {}%",
                rule.getName(), metrics.denyRate(), rule.getThresholdPercentage());
        }
    }

    /**
     * Calculate the deny rate for an alert rule's time window.
     *
     * @param rule The alert rule
     * @return Deny rate metrics
     */
    private DenyRateMetrics calculateDenyRate(AlertRule rule) {
        OffsetDateTime windowStart = OffsetDateTime.now().minusSeconds(rule.getWindowSeconds());
        OffsetDateTime windowEnd = OffsetDateTime.now();

        UUID policyId = rule.getPolicy().getId();

        long totalRequests = eventRepository.countByPolicyIdAndTimeBetween(
            policyId, windowStart, windowEnd);

        long deniedRequests = eventRepository.countDeniedByPolicyIdAndTimeBetween(
            policyId, windowStart, windowEnd);

        double denyRate = totalRequests > 0
            ? (deniedRequests * 100.0) / totalRequests
            : 0.0;

        log.debug("Deny rate for policy {}: {:.2f}% ({}/{} requests)",
            policyId, denyRate, deniedRequests, totalRequests);

        return new DenyRateMetrics(denyRate, totalRequests, deniedRequests);
    }

    /**
     * Check if an alert rule is currently in its cooldown period.
     *
     * @param rule The alert rule
     * @return true if in cooldown, false otherwise
     */
    private boolean isInCooldown(AlertRule rule) {
        if (rule.getLastTriggeredAt() == null) {
            return false;
        }

        OffsetDateTime cooldownEnd = rule.getLastTriggeredAt()
            .plusSeconds(rule.getCooldownSeconds());

        return OffsetDateTime.now().isBefore(cooldownEnd);
    }

    /**
     * Trigger an alert by sending notifications and updating the alert rule.
     *
     * @param rule The alert rule that was triggered
     * @param metrics The current deny rate metrics
     */
    @Transactional
    public void triggerAlert(AlertRule rule, DenyRateMetrics metrics) {
        Policy policy = rule.getPolicy();
        OffsetDateTime triggeredAt = OffsetDateTime.now();

        AlertNotification notification = AlertNotification.builder()
            .alertRuleId(rule.getId())
            .alertRuleName(rule.getName())
            .policyId(policy.getId())
            .policyName(policy.getName())
            .currentDenyRate(metrics.denyRate())
            .thresholdPercentage(rule.getThresholdPercentage())
            .windowSeconds(rule.getWindowSeconds())
            .totalRequests(metrics.totalRequests())
            .deniedRequests(metrics.deniedRequests())
            .triggeredAt(triggeredAt)
            .build();

        // Send notifications to all enabled channels
        sendNotifications(notification);

        // Update lastTriggeredAt to start cooldown period
        rule.setLastTriggeredAt(triggeredAt);
        alertRuleRepository.save(rule);

        log.info("Alert triggered and notifications sent for rule: {}", rule.getName());
    }

    /**
     * Send notifications to all enabled notification channels.
     *
     * Failures in individual notifiers are logged but don't affect other notifiers.
     *
     * @param notification The alert notification to send
     */
    public void sendNotifications(AlertNotification notification) {
        List<AlertNotificationAdapter> enabledNotifiers = notifiers.stream()
            .filter(AlertNotificationAdapter::isEnabled)
            .toList();

        if (enabledNotifiers.isEmpty()) {
            log.warn("No enabled notifiers found - alert will not be sent");
            return;
        }

        log.info("Sending alert to {} notifier(s): {}",
            enabledNotifiers.size(),
            enabledNotifiers.stream().map(AlertNotificationAdapter::getName).toList());

        for (AlertNotificationAdapter notifier : enabledNotifiers) {
            try {
                notifier.sendNotification(notification);
                log.debug("Notification sent via {}", notifier.getName());
            } catch (Exception e) {
                log.error("Failed to send notification via {}: {}",
                    notifier.getName(), e.getMessage(), e);
                // Continue with other notifiers
            }
        }
    }

    /**
     * Internal record to hold deny rate calculation results.
     *
     * @param denyRate Percentage of denied requests (0-100)
     * @param totalRequests Total number of requests in the window
     * @param deniedRequests Number of denied requests in the window
     */
    private record DenyRateMetrics(
        double denyRate,
        long totalRequests,
        long deniedRequests
    ) {}
}
