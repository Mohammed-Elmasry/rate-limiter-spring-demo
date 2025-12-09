package com.example.ratelimiter.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener for Circuit Breaker state transitions.
 *
 * This listener monitors circuit breaker events and:
 * 1. Logs state transitions for observability
 * 2. Emits metrics for monitoring and alerting
 * 3. Provides insights into system health
 *
 * State Transitions:
 * - CLOSED -> OPEN: Failure rate exceeded, circuit opens
 * - OPEN -> HALF_OPEN: Testing if service recovered
 * - HALF_OPEN -> CLOSED: Service recovered, circuit closes
 * - HALF_OPEN -> OPEN: Service still failing, circuit reopens
 *
 * Metrics:
 * - Circuit breaker state changes (by name and state)
 * - Failure counts
 * - Success counts
 * - Retry attempts
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerEventListener {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final MeterRegistry meterRegistry;

    /**
     * Registers event listeners after application startup.
     * This ensures all circuit breakers are registered before we attach listeners.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerEventListeners() {
        log.info("Registering Circuit Breaker event listeners");

        // Register listeners for all circuit breakers
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerCircuitBreakerListeners);

        // Register listeners for all retry instances
        retryRegistry.getAllRetries().forEach(this::registerRetryListeners);
    }

    /**
     * Registers event listeners for a specific circuit breaker.
     *
     * @param circuitBreaker The circuit breaker to monitor
     */
    private void registerCircuitBreakerListeners(CircuitBreaker circuitBreaker) {
        String cbName = circuitBreaker.getName();
        log.info("Registering listeners for circuit breaker: {}", cbName);

        // State transition events (most critical)
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> handleStateTransition(event, cbName));

        // Success events
        circuitBreaker.getEventPublisher()
                .onSuccess(event -> {
                    log.debug("[{}] Circuit breaker recorded success", cbName);
                    incrementCounter("resilience4j.circuitbreaker.success", cbName);
                });

        // Error events
        circuitBreaker.getEventPublisher()
                .onError(event -> {
                    log.warn("[{}] Circuit breaker recorded error: {} - {}",
                            cbName,
                            event.getThrowable().getClass().getSimpleName(),
                            event.getThrowable().getMessage());
                    incrementCounter("resilience4j.circuitbreaker.error", cbName);
                });

        // Call not permitted (circuit is open)
        circuitBreaker.getEventPublisher()
                .onCallNotPermitted(event -> {
                    log.warn("[{}] Call not permitted - circuit is OPEN", cbName);
                    incrementCounter("resilience4j.circuitbreaker.call_not_permitted", cbName);
                });

        // Ignored error (doesn't count towards failure rate)
        circuitBreaker.getEventPublisher()
                .onIgnoredError(event -> {
                    log.debug("[{}] Error ignored: {}",
                            cbName,
                            event.getThrowable().getMessage());
                    incrementCounter("resilience4j.circuitbreaker.ignored_error", cbName);
                });
    }

    /**
     * Handles circuit breaker state transitions with detailed logging and metrics.
     *
     * @param event The state transition event
     * @param cbName The circuit breaker name
     */
    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event, String cbName) {
        var fromState = event.getStateTransition().getFromState();
        var toState = event.getStateTransition().getToState();

        log.warn("[{}] Circuit breaker state transition: {} -> {}",
                cbName, fromState, toState);

        // Emit detailed metrics
        incrementCounter("resilience4j.circuitbreaker.state_transition", cbName, toState.name());

        // Get circuit breaker for metrics
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker(cbName);
        var metrics = circuitBreaker.getMetrics();

        // Special handling for critical transitions
        switch (toState) {
            case OPEN:
                log.error("[{}] CRITICAL: Circuit breaker is now OPEN - calls will fail fast. " +
                                "Failure rate: {}%, Slow call rate: {}%",
                        cbName,
                        metrics.getFailureRate(),
                        metrics.getSlowCallRate());
                break;

            case HALF_OPEN:
                log.info("[{}] Circuit breaker is now HALF_OPEN - testing service recovery",
                        cbName);
                break;

            case CLOSED:
                if (fromState == CircuitBreaker.State.HALF_OPEN) {
                    log.info("[{}] SUCCESS: Circuit breaker is now CLOSED - service has recovered",
                            cbName);
                }
                break;

            case FORCED_OPEN:
                log.error("[{}] Circuit breaker manually forced OPEN", cbName);
                break;

            default:
                log.warn("[{}] Circuit breaker in unexpected state: {}", cbName, toState);
        }
    }

    /**
     * Registers event listeners for retry instances.
     *
     * @param retry The retry instance to monitor
     */
    private void registerRetryListeners(Retry retry) {
        String retryName = retry.getName();
        log.info("Registering listeners for retry: {}", retryName);

        // Retry event
        retry.getEventPublisher()
                .onRetry(event -> {
                    log.warn("[{}] Retry attempt {} of {}: {}",
                            retryName,
                            event.getNumberOfRetryAttempts(),
                            retry.getRetryConfig().getMaxAttempts(),
                            event.getLastThrowable().getMessage());
                    incrementCounter("resilience4j.retry.attempt", retryName);
                });

        // Success after retry
        retry.getEventPublisher()
                .onSuccess(event -> {
                    if (event.getNumberOfRetryAttempts() > 0) {
                        log.info("[{}] Success after {} retry attempts",
                                retryName,
                                event.getNumberOfRetryAttempts());
                    }
                    incrementCounter("resilience4j.retry.success", retryName);
                });

        // All retries exhausted
        retry.getEventPublisher()
                .onError(event -> {
                    log.error("[{}] All retry attempts exhausted after {} attempts: {}",
                            retryName,
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage());
                    incrementCounter("resilience4j.retry.exhausted", retryName);
                });
    }

    /**
     * Increments a counter metric.
     *
     * @param name The metric name
     * @param tags Additional tags
     */
    private void incrementCounter(String name, String... tags) {
        try {
            Counter.builder(name)
                    .tags(buildTags(tags))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("Failed to increment counter {}: {}", name, e.getMessage());
        }
    }

    /**
     * Builds tag array from variable arguments.
     * Tags should be provided in pairs: key, value, key, value, ...
     *
     * @param tags Variable arguments representing tags
     * @return Tag array
     */
    private String[] buildTags(String... tags) {
        if (tags.length == 1) {
            // Single tag is the circuit breaker/retry name
            return new String[]{"name", tags[0]};
        } else if (tags.length == 2) {
            // Two tags: name and state
            return new String[]{"name", tags[0], "state", tags[1]};
        }
        return tags;
    }
}
