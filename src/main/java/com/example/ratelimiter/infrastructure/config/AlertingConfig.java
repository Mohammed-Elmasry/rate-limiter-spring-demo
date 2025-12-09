package com.example.ratelimiter.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the alerting system.
 *
 * This configuration class:
 * - Enables scheduled tasks for alert checking
 * - Provides RestTemplate for webhook and Slack notifications
 * - Configures ObjectMapper for JSON serialization
 *
 * Thread Safety: All beans are thread-safe singletons.
 */
@Configuration
@EnableScheduling
public class AlertingConfig {

    /**
     * RestTemplate for sending HTTP notifications (Slack, webhooks).
     *
     * Configured with reasonable timeouts to prevent hanging on slow endpoints.
     * Thread-safe and can be used concurrently by multiple notifiers.
     *
     * @param builder RestTemplate builder provided by Spring Boot
     * @return Configured RestTemplate
     */
    @Bean
    public RestTemplate alertRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * ObjectMapper for JSON serialization in notification payloads.
     *
     * Uses the default Spring Boot configured ObjectMapper.
     * Thread-safe and can be used concurrently.
     *
     * @return ObjectMapper instance
     */
    @Bean
    public ObjectMapper alertObjectMapper() {
        return new ObjectMapper()
            .findAndRegisterModules(); // Registers JavaTimeModule for OffsetDateTime, etc.
    }
}
