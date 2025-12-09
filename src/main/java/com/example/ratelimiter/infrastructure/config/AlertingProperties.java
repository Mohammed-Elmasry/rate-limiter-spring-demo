package com.example.ratelimiter.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration properties for the alerting system.
 *
 * Binds to application.yml properties under 'alerting' prefix.
 * Provides type-safe access to alerting configuration.
 *
 * Example configuration:
 * <pre>
 * alerting:
 *   enabled: true
 *   check-interval: 60s
 *   notifiers:
 *     slack:
 *       enabled: false
 *       webhook-url: ${SLACK_WEBHOOK_URL:}
 *     email:
 *       enabled: false
 *       from: alerts@ratelimiter.com
 *       to: ops@company.com
 *     webhook:
 *       enabled: false
 *       url: ${ALERT_WEBHOOK_URL:}
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "alerting")
public class AlertingProperties {

    /**
     * Master switch to enable/disable the entire alerting system.
     */
    private boolean enabled = true;

    /**
     * How often to check alert rules (in seconds).
     * Default: 60 seconds
     */
    private Duration checkInterval = Duration.ofSeconds(60);

    /**
     * Configuration for all notification channels.
     */
    private Notifiers notifiers = new Notifiers();

    @Data
    public static class Notifiers {
        private SlackNotifierConfig slack = new SlackNotifierConfig();
        private EmailNotifierConfig email = new EmailNotifierConfig();
        private WebhookNotifierConfig webhook = new WebhookNotifierConfig();
    }

    @Data
    public static class SlackNotifierConfig {
        /**
         * Enable Slack notifications.
         */
        private boolean enabled = false;

        /**
         * Slack incoming webhook URL.
         * Example: https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXX
         */
        private String webhookUrl;
    }

    @Data
    public static class EmailNotifierConfig {
        /**
         * Enable email notifications.
         */
        private boolean enabled = false;

        /**
         * From email address.
         */
        private String from = "alerts@ratelimiter.com";

        /**
         * Comma-separated list of recipient email addresses.
         */
        private String to;
    }

    @Data
    public static class WebhookNotifierConfig {
        /**
         * Enable generic webhook notifications.
         */
        private boolean enabled = false;

        /**
         * Webhook endpoint URL.
         */
        private String url;
    }
}
