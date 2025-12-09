package com.example.ratelimiter.infrastructure.notification;

import com.example.ratelimiter.application.dto.AlertNotification;
import com.example.ratelimiter.infrastructure.config.AlertingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic webhook-based alert notifier.
 *
 * Sends JSON-formatted alerts to a configured webhook URL.
 * This allows integration with any system that accepts webhook notifications
 * (PagerDuty, OpsGenie, custom alerting systems, etc.)
 *
 * Thread Safety: This class is thread-safe. RestTemplate is thread-safe,
 * and all state is immutable or thread-safe.
 *
 * Configuration:
 * - alerting.notifiers.webhook.enabled=true
 * - alerting.notifiers.webhook.url=https://your-webhook-endpoint.com/alerts
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "alerting.notifiers.webhook", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WebhookNotifier implements AlertNotificationAdapter {

    private final AlertingProperties alertingProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void sendNotification(AlertNotification notification) {
        try {
            String webhookUrl = alertingProperties.getNotifiers().getWebhook().getUrl();

            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("Webhook URL is not configured");
                return;
            }

            Map<String, Object> payload = buildWebhookPayload(notification);
            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);

            log.info("Webhook notification sent for alert: {}", notification.alertRuleName());
        } catch (Exception e) {
            log.error("Failed to send webhook notification: {}", e.getMessage(), e);
            // Don't rethrow - we don't want notification failures to disrupt the system
        }
    }

    /**
     * Build the webhook payload with all alert information.
     *
     * The payload follows a standardized format that can be easily consumed
     * by various alerting systems.
     *
     * @param notification The alert notification
     * @return Webhook payload map
     */
    private Map<String, Object> buildWebhookPayload(AlertNotification notification) {
        Map<String, Object> payload = new HashMap<>();

        // Alert metadata
        payload.put("alert_type", "rate_limiter");
        payload.put("severity", getSeverityLevel(notification.currentDenyRate()));
        payload.put("timestamp", notification.triggeredAt().toString());

        // Alert rule information
        Map<String, Object> alertRule = new HashMap<>();
        alertRule.put("id", notification.alertRuleId().toString());
        alertRule.put("name", notification.alertRuleName());
        payload.put("alert_rule", alertRule);

        // Policy information
        Map<String, Object> policy = new HashMap<>();
        policy.put("id", notification.policyId().toString());
        policy.put("name", notification.policyName());
        payload.put("policy", policy);

        // Metrics
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("current_deny_rate", notification.currentDenyRate());
        metrics.put("threshold_percentage", notification.thresholdPercentage());
        metrics.put("window_seconds", notification.windowSeconds());
        metrics.put("total_requests", notification.totalRequests());
        metrics.put("denied_requests", notification.deniedRequests());
        payload.put("metrics", metrics);

        // Human-readable message
        payload.put("message", notification.toMessage());

        return payload;
    }

    /**
     * Determine the severity level based on deny rate.
     *
     * @param denyRate Current deny rate percentage
     * @return Severity level string
     */
    private String getSeverityLevel(double denyRate) {
        if (denyRate >= 80.0) {
            return "critical";
        } else if (denyRate >= 50.0) {
            return "warning";
        } else {
            return "attention";
        }
    }

    @Override
    public boolean isEnabled() {
        String webhookUrl = alertingProperties.getNotifiers().getWebhook().getUrl();
        return alertingProperties.getNotifiers().getWebhook().isEnabled() &&
               webhookUrl != null && !webhookUrl.isBlank();
    }

    @Override
    public String getName() {
        return "Webhook";
    }
}
