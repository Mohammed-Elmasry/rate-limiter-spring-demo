package com.example.ratelimiter.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Configuration for Resilience4j Circuit Breaker and Retry patterns.
 *
 * This configuration provides resilience for:
 * 1. Redis operations - protects against Redis failures
 * 2. Database operations - protects against database failures
 *
 * Circuit Breaker Pattern:
 * - CLOSED: Normal operation, requests flow through
 * - OPEN: Failure threshold exceeded, requests fail fast without calling the service
 * - HALF_OPEN: Testing if service has recovered, limited requests are allowed
 *
 * Configuration Strategy:
 * - Redis: Shorter wait duration (5s) for faster recovery detection
 * - Database: Longer wait duration (10s) as DB recovery typically takes longer
 * - Both use 50% failure rate threshold to balance sensitivity and stability
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ResilienceConfig {

    /**
     * Creates a CircuitBreakerRegistry with default configuration.
     * Individual instances are configured via application.yml
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .failureRateThreshold(50)
                .recordExceptions(
                        RedisConnectionFailureException.class,
                        RedisSystemException.class,
                        IOException.class,
                        TimeoutException.class
                )
                .build();

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    /**
     * Creates a RetryRegistry with default configuration.
     * Individual instances are configured via application.yml
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(
                        RedisConnectionFailureException.class,
                        TimeoutException.class
                )
                .build();

        return RetryRegistry.of(defaultConfig);
    }

    /**
     * Redis circuit breaker for protecting against Redis failures.
     * Configured with shorter recovery time for faster service restoration.
     */
    @Bean
    public CircuitBreaker redisCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("redis");

        // Log circuit breaker state changes
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Redis Circuit Breaker state changed from {} to {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                })
                .onError(event -> {
                    log.debug("Redis Circuit Breaker recorded error: {}",
                            event.getThrowable().getMessage());
                })
                .onSuccess(event -> {
                    log.trace("Redis Circuit Breaker recorded success");
                });

        return circuitBreaker;
    }

    /**
     * Database circuit breaker for protecting against database failures.
     * Configured with longer recovery time as database recovery typically takes longer.
     */
    @Bean
    public CircuitBreaker databaseCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("database");

        // Log circuit breaker state changes
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Database Circuit Breaker state changed from {} to {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                })
                .onError(event -> {
                    log.debug("Database Circuit Breaker recorded error: {}",
                            event.getThrowable().getMessage());
                })
                .onSuccess(event -> {
                    log.trace("Database Circuit Breaker recorded success");
                });

        return circuitBreaker;
    }

    /**
     * Redis retry for transient failures.
     */
    @Bean
    public Retry redisRetry(RetryRegistry registry) {
        Retry retry = registry.retry("redis");

        // Log retry attempts
        retry.getEventPublisher()
                .onRetry(event -> {
                    log.debug("Redis Retry attempt {} of {}: {}",
                            event.getNumberOfRetryAttempts(),
                            retry.getRetryConfig().getMaxAttempts(),
                            event.getLastThrowable().getMessage());
                })
                .onError(event -> {
                    log.error("Redis Retry exhausted after {} attempts",
                            event.getNumberOfRetryAttempts());
                });

        return retry;
    }

    /**
     * Database retry for transient failures.
     */
    @Bean
    public Retry databaseRetry(RetryRegistry registry) {
        Retry retry = registry.retry("database");

        // Log retry attempts
        retry.getEventPublisher()
                .onRetry(event -> {
                    log.debug("Database Retry attempt {} of {}: {}",
                            event.getNumberOfRetryAttempts(),
                            retry.getRetryConfig().getMaxAttempts(),
                            event.getLastThrowable().getMessage());
                })
                .onError(event -> {
                    log.error("Database Retry exhausted after {} attempts",
                            event.getNumberOfRetryAttempts());
                });

        return retry;
    }
}
