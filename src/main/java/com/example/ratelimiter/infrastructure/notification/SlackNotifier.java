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
 * Slack webhook-based alert notifier.
 *
 * Sends formatted alerts to a Slack channel using incoming webhooks.
 * Messages are formatted with color coding based on severity.
 *
 * Thread Safety: This class is thread-safe. RestTemplate is thread-safe,
 * and all state is immutable or thread-safe.
 *
 * Configuration:
 * - alerting.notifiers.slack.enabled=true
 * - alerting.notifiers.slack.webhook-url=https://hooks.slack.com/services/...
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "alerting.notifiers.slack", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SlackNotifier implements AlertNotificationAdapter {

    private final AlertingProperties alertingProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void sendNotification(AlertNotification notification) {
        try {
            String webhookUrl = alertingProperties.getNotifiers().getSlack().getWebhookUrl();

            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("Slack webhook URL is not configured");
                return;
            }

            Map<String, Object> slackMessage = buildSlackMessage(notification);
            String jsonPayload = objectMapper.writeValueAsString(slackMessage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);

            log.info("Slack notification sent for alert: {}", notification.alertRuleName());
        } catch (Exception e) {
            log.error("Failed to send Slack notification: {}", e.getMessage(), e);
            // Don't rethrow - we don't want notification failures to disrupt the system
        }
    }

    /**
     * Build a Slack message with rich formatting using Block Kit.
     *
     * @param notification The alert notification
     * @return Slack message payload
     */
    private Map<String, Object> buildSlackMessage(AlertNotification notification) {
        Map<String, Object> message = new HashMap<>();

        // Determine color based on severity
        String color = getColorForDenyRate(notification.currentDenyRate());

        // Build attachment with formatted message
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", color);
        attachment.put("title", "Rate Limiter Alert: " + notification.alertRuleName());
        attachment.put("text", buildAlertText(notification));
        attachment.put("footer", "Rate Limiter Alert System");
        attachment.put("ts", notification.triggeredAt().toEpochSecond());

        message.put("attachments", new Object[]{attachment});

        return message;
    }

    /**
     * Build the main alert text with all details.
     *
     * @param notification The alert notification
     * @return Formatted alert text
     */
    private String buildAlertText(AlertNotification notification) {
        return String.format(
            "*Policy:* %s (ID: `%s`)\n" +
            "*Current Deny Rate:* %.2f%% (Threshold: %d%%)\n" +
            "*Time Window:* %d seconds\n" +
            "*Total Requests:* %,d\n" +
            "*Denied Requests:* %,d\n" +
            "*Triggered At:* %s",
            notification.policyName(),
            notification.policyId(),
            notification.currentDenyRate(),
            notification.thresholdPercentage(),
            notification.windowSeconds(),
            notification.totalRequests(),
            notification.deniedRequests(),
            notification.triggeredAt()
        );
    }

    /**
     * Determine the color for the Slack attachment based on deny rate.
     * - Red: >= 80% deny rate
     * - Orange: >= 50% deny rate
     * - Yellow: < 50% deny rate
     *
     * @param denyRate Current deny rate percentage
     * @return Hex color code
     */
    private String getColorForDenyRate(double denyRate) {
        if (denyRate >= 80.0) {
            return "#d32f2f"; // Red - Critical
        } else if (denyRate >= 50.0) {
            return "#f57c00"; // Orange - Warning
        } else {
            return "#fbc02d"; // Yellow - Attention
        }
    }

    @Override
    public boolean isEnabled() {
        String webhookUrl = alertingProperties.getNotifiers().getSlack().getWebhookUrl();
        return alertingProperties.getNotifiers().getSlack().isEnabled() &&
               webhookUrl != null && !webhookUrl.isBlank();
    }

    @Override
    public String getName() {
        return "Slack";
    }
}
