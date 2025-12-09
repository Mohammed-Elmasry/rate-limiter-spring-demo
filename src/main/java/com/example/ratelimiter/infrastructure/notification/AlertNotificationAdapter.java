package com.example.ratelimiter.infrastructure.notification;

import com.example.ratelimiter.application.dto.AlertNotification;

/**
 * Interface for alert notification adapters.
 *
 * Implementations provide different notification channels:
 * - Slack
 * - Email
 * - Webhook
 * - Logging
 *
 * Thread Safety: Implementations must be thread-safe as they will be called
 * from scheduled tasks and potentially multiple threads.
 */
public interface AlertNotificationAdapter {

    /**
     * Send an alert notification.
     *
     * This method should be non-blocking and handle errors gracefully.
     * Implementations should log errors but not throw exceptions to prevent
     * one failing notifier from blocking others.
     *
     * @param notification The alert notification to send
     */
    void sendNotification(AlertNotification notification);

    /**
     * Check if this notifier is enabled and properly configured.
     *
     * @return true if the notifier is ready to send notifications
     */
    boolean isEnabled();

    /**
     * Get the name of this notifier (for logging purposes).
     *
     * @return Notifier name (e.g., "Slack", "Email", "Webhook")
     */
    String getName();
}
