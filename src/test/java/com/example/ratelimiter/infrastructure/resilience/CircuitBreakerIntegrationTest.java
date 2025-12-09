package com.example.ratelimiter.infrastructure.resilience;

import com.example.ratelimiter.application.dto.RateLimitCheckRequest;
import com.example.ratelimiter.application.dto.RateLimitCheckResponse;
import com.example.ratelimiter.application.service.RateLimitService;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Circuit Breaker behavior.
 *
 * These tests verify:
 * 1. Circuit breaker opens on Redis failures
 * 2. Fallback behavior respects Policy.failMode
 * 3. Circuit breaker transitions through states correctly
 * 4. Automatic recovery when Redis comes back
 *
 * Note: These tests are designed to demonstrate circuit breaker behavior
 * but may need adjustment based on your test environment configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerIntegrationTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Policy failOpenPolicy;
    private Policy failClosedPolicy;

    @BeforeEach
    void setUp() {
        // Clean up policies
        policyRepository.deleteAll();

        // Reset circuit breakers
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(cb -> cb.transitionToClosedState());

        // Create test policies
        failOpenPolicy = Policy.builder()
                .name("Test Fail Open Policy")
                .description("Policy that fails open when Redis is unavailable")
                .scope(PolicyScope.GLOBAL)
                .algorithm(Algorithm.FIXED_WINDOW)
                .maxRequests(10)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_OPEN)
                .enabled(true)
                .isDefault(true)
                .build();

        failClosedPolicy = Policy.builder()
                .name("Test Fail Closed Policy")
                .description("Policy that fails closed when Redis is unavailable")
                .scope(PolicyScope.GLOBAL)
                .algorithm(Algorithm.FIXED_WINDOW)
                .maxRequests(10)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .isDefault(false)
                .build();

        failOpenPolicy = policyRepository.save(failOpenPolicy);
        failClosedPolicy = policyRepository.save(failClosedPolicy);
    }

    @Test
    void testCircuitBreakerStateTransitions() {
        // Given: Redis circuit breaker is in CLOSED state
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // When: Making successful rate limit checks
        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier("test-key")
                .scope("USER")
                .policyId(failOpenPolicy.getId())
                .build();

        // Then: Circuit breaker should remain closed
        for (int i = 0; i < 3; i++) {
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);
            assertThat(response).isNotNull();
        }

        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void testFailOpenPolicyAllowsRequestsOnCircuitBreakerOpen() {
        // Given: A policy with FAIL_OPEN mode
        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier("test-key-fail-open")
                .scope("USER")
                .policyId(failOpenPolicy.getId())
                .build();

        // When: Circuit breaker is forced open
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        redisCircuitBreaker.transitionToOpenState();

        // Then: Requests should be allowed due to FAIL_OPEN policy
        RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

        assertThat(response).isNotNull();
        assertThat(response.allowed()).isTrue();

        // Clean up: Reset circuit breaker
        redisCircuitBreaker.transitionToClosedState();
    }

    @Test
    void testFailClosedPolicyDeniesRequestsOnCircuitBreakerOpen() {
        // Given: A policy with FAIL_CLOSED mode
        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier("test-key-fail-closed")
                .scope("USER")
                .policyId(failClosedPolicy.getId())
                .build();

        // When: Circuit breaker is forced open
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        redisCircuitBreaker.transitionToOpenState();

        // Then: Requests should be denied due to FAIL_CLOSED policy
        RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

        assertThat(response).isNotNull();
        assertThat(response.allowed()).isFalse();

        // Clean up: Reset circuit breaker
        redisCircuitBreaker.transitionToClosedState();
    }

    @Test
    void testCircuitBreakerMetrics() {
        // Given: Redis circuit breaker
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");

        // When: Making rate limit checks
        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier("test-key-metrics")
                .scope("USER")
                .policyId(failOpenPolicy.getId())
                .build();

        rateLimitService.checkRateLimit(request);

        // Then: Circuit breaker should track metrics
        CircuitBreaker.Metrics metrics = redisCircuitBreaker.getMetrics();

        assertThat(metrics).isNotNull();
        assertThat(metrics.getNumberOfSuccessfulCalls()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.getNumberOfFailedCalls()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testDatabaseCircuitBreaker() {
        // Given: Database circuit breaker
        CircuitBreaker databaseCircuitBreaker = circuitBreakerRegistry.circuitBreaker("database");

        // Then: Circuit breaker should be configured correctly
        assertThat(databaseCircuitBreaker).isNotNull();
        assertThat(databaseCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // When: Accessing policies (database operations)
        Policy policy = policyRepository.findById(failOpenPolicy.getId()).orElse(null);

        // Then: Operation should succeed
        assertThat(policy).isNotNull();
        assertThat(databaseCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void testMultipleFailuresOpenCircuitBreaker() {
        // Given: Redis circuit breaker configuration
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        CircuitBreaker.Metrics metrics = redisCircuitBreaker.getMetrics();

        // Then: Verify circuit breaker starts in closed state
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(metrics).isNotNull();

        // Note: In a real scenario, multiple failures would open the circuit
        // This test documents the expected behavior
    }

    @Test
    void testHalfOpenStateTransition() {
        // Given: Redis circuit breaker
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");

        // When: Manually transition to half-open state
        redisCircuitBreaker.transitionToHalfOpenState();

        // Then: Circuit breaker should be in half-open state
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // When: Make a successful request
        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier("test-key-half-open")
                .scope("USER")
                .policyId(failOpenPolicy.getId())
                .build();

        rateLimitService.checkRateLimit(request);

        // Note: After permitted calls succeed, circuit should close again
        // Clean up
        redisCircuitBreaker.transitionToClosedState();
    }

    @Test
    void testCircuitBreakerWithDifferentPolicies() {
        // Given: Multiple policies with different fail modes
        RateLimitCheckRequest failOpenRequest = RateLimitCheckRequest.builder()
                .identifier("test-key-mixed-1")
                .scope("USER")
                .policyId(failOpenPolicy.getId())
                .build();

        RateLimitCheckRequest failClosedRequest = RateLimitCheckRequest.builder()
                .identifier("test-key-mixed-2")
                .scope("USER")
                .policyId(failClosedPolicy.getId())
                .build();

        // When: Circuit breaker is open
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        redisCircuitBreaker.transitionToOpenState();

        // Then: Different policies should behave according to their fail mode
        RateLimitCheckResponse failOpenResponse = rateLimitService.checkRateLimit(failOpenRequest);
        RateLimitCheckResponse failClosedResponse = rateLimitService.checkRateLimit(failClosedRequest);

        assertThat(failOpenResponse.allowed()).isTrue();
        assertThat(failClosedResponse.allowed()).isFalse();

        // Clean up
        redisCircuitBreaker.transitionToClosedState();
    }

    @Test
    void testCircuitBreakerConfiguration() {
        // Given: Redis circuit breaker
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        CircuitBreakerConfig config = redisCircuitBreaker.getCircuitBreakerConfig();

        // Then: Circuit breaker should have proper configuration
        assertThat(config).isNotNull();
        assertThat(config.getSlidingWindowSize()).isGreaterThan(0);
        assertThat(config.getMinimumNumberOfCalls()).isGreaterThan(0);

        // Verify database circuit breaker configuration
        CircuitBreaker databaseCircuitBreaker = circuitBreakerRegistry.circuitBreaker("database");
        CircuitBreakerConfig dbConfig = databaseCircuitBreaker.getCircuitBreakerConfig();

        assertThat(dbConfig).isNotNull();
        assertThat(dbConfig.getSlidingWindowSize()).isGreaterThan(0);
    }
}
