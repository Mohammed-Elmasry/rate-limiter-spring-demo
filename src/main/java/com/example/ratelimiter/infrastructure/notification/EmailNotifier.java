package com.example.ratelimiter.infrastructure.notification;

import com.example.ratelimiter.application.dto.AlertNotification;
import com.example.ratelimiter.infrastructure.config.AlertingProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email-based alert notifier.
 *
 * Sends formatted HTML email alerts using JavaMailSender.
 * Emails include detailed alert information and are formatted for readability.
 *
 * Thread Safety: JavaMailSender implementations are typically thread-safe.
 * This class is thread-safe as all state is immutable or thread-safe.
 *
 * Configuration:
 * - alerting.notifiers.email.enabled=true
 * - alerting.notifiers.email.from=alerts@ratelimiter.com
 * - alerting.notifiers.email.to=ops@company.com
 * - spring.mail.host, spring.mail.port, etc.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "alerting.notifiers.email", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EmailNotifier implements AlertNotificationAdapter {

    private final AlertingProperties alertingProperties;
    private final JavaMailSender mailSender;

    @Override
    public void sendNotification(AlertNotification notification) {
        try {
            AlertingProperties.EmailNotifierConfig emailConfig = alertingProperties.getNotifiers().getEmail();

            if (!isValidConfiguration(emailConfig)) {
                log.warn("Email notifier is not properly configured");
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailConfig.getFrom());
            helper.setTo(emailConfig.getTo().split(","));
            helper.setSubject(buildSubject(notification));
            helper.setText(buildHtmlBody(notification), true);

            mailSender.send(message);

            log.info("Email notification sent for alert: {}", notification.alertRuleName());
        } catch (MessagingException e) {
            log.error("Failed to send email notification: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending email notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Build the email subject line.
     *
     * @param notification The alert notification
     * @return Email subject
     */
    private String buildSubject(AlertNotification notification) {
        return String.format(
            "[ALERT] Rate Limiter: %s - %.2f%% Deny Rate",
            notification.alertRuleName(),
            notification.currentDenyRate()
        );
    }

    /**
     * Build the HTML email body with formatting.
     *
     * @param notification The alert notification
     * @return HTML email body
     */
    private String buildHtmlBody(AlertNotification notification) {
        String severity = getSeverityLabel(notification.currentDenyRate());
        String severityColor = getSeverityColor(notification.currentDenyRate());

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: %s; color: white; padding: 15px; border-radius: 5px; }
                    .content { background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px; }
                    .metric { margin: 10px 0; }
                    .metric-label { font-weight: bold; display: inline-block; width: 180px; }
                    .metric-value { color: #555; }
                    .footer { margin-top: 20px; font-size: 12px; color: #888; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>Rate Limiter Alert - %s</h2>
                        <p>Severity: %s</p>
                    </div>
                    <div class="content">
                        <h3>Alert Details</h3>
                        <div class="metric">
                            <span class="metric-label">Alert Rule:</span>
                            <span class="metric-value">%s</span>
                        </div>
                        <div class="metric">
                            <span class="metric-label">Policy:</span>
                            <span class="metric-value">%s (ID: %s)</span>
                        </div>
                        <div class="metric">
                            <span class="metric-label">Current Deny Rate:</span>
                            <span class="metric-value">%.2f%%</span>
                        </div>
                        <div class="metric">
                            <span class="metric-label">Threshold:</span>
                            <span class="metric-value">%d%%</span>
                        </div>
                        <div class="metric">
                            <span class="metric-label">Time Window:</span>
                            <span class="metric-value">%d seconds</span>
                        </div>
                        <div class="metric">
                            <span class="metric-label">Total Requests:</span>
                            <span class="metric-value">%,d</span>
                        </div>
                        <div class="metric">
                            <span class="metric-label">Denied Requests:</span>
                            <span class="metric-value">%,d</span>
                        </div>
                        <div class="metric">
                            <span class="metric-label">Triggered At:</span>
                            <span class="metric-value">%s</span>
                        </div>
                    </div>
                    <div class="footer">
                        <p>This is an automated alert from the Rate Limiter Alert System.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
            severityColor,
            notification.alertRuleName(),
            severity,
            notification.alertRuleName(),
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
     * Get a human-readable severity label.
     *
     * @param denyRate Current deny rate percentage
     * @return Severity label
     */
    private String getSeverityLabel(double denyRate) {
        if (denyRate >= 80.0) {
            return "CRITICAL";
        } else if (denyRate >= 50.0) {
            return "WARNING";
        } else {
            return "ATTENTION";
        }
    }

    /**
     * Get the color for the severity level.
     *
     * @param denyRate Current deny rate percentage
     * @return Hex color code
     */
    private String getSeverityColor(double denyRate) {
        if (denyRate >= 80.0) {
            return "#d32f2f"; // Red
        } else if (denyRate >= 50.0) {
            return "#f57c00"; // Orange
        } else {
            return "#fbc02d"; // Yellow
        }
    }

    /**
     * Validate email configuration.
     *
     * @param config Email notifier configuration
     * @return true if configuration is valid
     */
    private boolean isValidConfiguration(AlertingProperties.EmailNotifierConfig config) {
        return config != null &&
               config.getFrom() != null && !config.getFrom().isBlank() &&
               config.getTo() != null && !config.getTo().isBlank();
    }

    @Override
    public boolean isEnabled() {
        AlertingProperties.EmailNotifierConfig emailConfig = alertingProperties.getNotifiers().getEmail();
        return emailConfig.isEnabled() && isValidConfiguration(emailConfig);
    }

    @Override
    public String getName() {
        return "Email";
    }
}
