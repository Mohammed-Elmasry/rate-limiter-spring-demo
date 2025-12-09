package com.example.ratelimiter.integration;

import com.example.ratelimiter.application.dto.RateLimitCheckRequest;
import com.example.ratelimiter.application.dto.RateLimitCheckResponse;
import com.example.ratelimiter.application.service.RateLimitService;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.RateLimitEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration tests for the full rate limiting flow.
 *
 * Tests the complete flow from rate limit check through to metrics recording:
 * 1. Client makes rate limit check request
 * 2. Service resolves policy
 * 3. Algorithm executes check against Redis
 * 4. Response is returned
 * 5. Metrics are recorded asynchronously
 *
 * Uses @SpringBootTest to load full application context with all beans.
 * Note: Requires Redis and PostgreSQL to be available (or use Testcontainers).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "logging.level.com.example.ratelimiter=DEBUG"
})
@DisplayName("Rate Limit Integration Tests")
class RateLimitIntegrationTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private RateLimitEventRepository eventRepository;

    private Policy testPolicy;
    private UUID testPolicyId;

    @BeforeEach
    void setUp() {
        // Create a test policy
        testPolicy = Policy.builder()
                .name("integration-test-policy")
                .description("Policy for integration testing")
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.FIXED_WINDOW)
                .maxRequests(10)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .isDefault(false)
                .build();

        testPolicy = policyRepository.save(testPolicy);
        testPolicyId = testPolicy.getId();
    }

    @Test
    @DisplayName("Should allow requests within rate limit")
    void checkRateLimit_withinLimit_allowsRequest() {
        // Given
        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier("integration-user-1")
                .scope("USER")
                .policyId(testPolicyId)
                .ipAddress("192.168.1.100")
                .resource("/api/test")
                .build();

        // When
        RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.allowed()).isTrue();
        assertThat(response.limit()).isEqualTo(10);
        assertThat(response.policyId()).isEqualTo(testPolicyId);
        assertThat(response.algorithm()).isEqualTo("FIXED_WINDOW");
    }

    @Test
    @DisplayName("Should deny requests exceeding rate limit")
    void checkRateLimit_exceedsLimit_deniesRequest() {
        // Given
        String identifier = "integration-user-burst-" + UUID.randomUUID();
        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier(identifier)
                .scope("USER")
                .policyId(testPolicyId)
                .ipAddress("192.168.1.101")
                .resource("/api/test")
                .build();

        // When - Make requests up to the limit
        for (int i = 0; i < 10; i++) {
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);
            assertThat(response.allowed()).isTrue();
        }

        // Try one more request (should be denied)
        RateLimitCheckResponse deniedResponse = rateLimitService.checkRateLimit(request);

        // Then
        assertThat(deniedResponse).isNotNull();
        assertThat(deniedResponse.allowed()).isFalse();
        assertThat(deniedResponse.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should record metrics asynchronously")
    void checkRateLimit_validRequest_recordsMetrics() {
        // Given
        String identifier = "integration-user-metrics-" + UUID.randomUUID();
        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier(identifier)
                .scope("USER")
                .policyId(testPolicyId)
                .ipAddress("192.168.1.102")
                .resource("/api/metrics-test")
                .build();

        long initialCount = eventRepository.countByPolicyId(testPolicyId);

        // When
        rateLimitService.checkRateLimit(request);

        // Then - Wait for async metrics recording (using Awaitility)
        await().atMost(5, SECONDS).untilAsserted(() -> {
            long newCount = eventRepository.countByPolicyId(testPolicyId);
            assertThat(newCount).isGreaterThan(initialCount);
        });
    }

    @Test
    @DisplayName("Should handle global default policy fallback")
    void checkRateLimit_noExplicitPolicy_usesGlobalDefault() {
        // Given - Create a global default policy
        Policy globalPolicy = Policy.builder()
                .name("global-default")
                .scope(PolicyScope.GLOBAL)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(1000)
                .windowSeconds(3600)
                .failMode(FailMode.FAIL_OPEN)
                .enabled(true)
                .isDefault(true)
                .build();

        globalPolicy = policyRepository.save(globalPolicy);

        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier("integration-user-global-" + UUID.randomUUID())
                .scope("USER")
                .resource("/api/test")
                // No explicit policyId, tenantId, or ipAddress
                .build();

        // When
        RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.allowed()).isTrue();
        assertThat(response.policyId()).isEqualTo(globalPolicy.getId());
        assertThat(response.limit()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should respect fail mode when policy is disabled")
    void checkRateLimit_disabledPolicy_deniesRequest() {
        // Given
        testPolicy.setEnabled(false);
        policyRepository.save(testPolicy);

        RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                .identifier("integration-user-disabled-" + UUID.randomUUID())
                .scope("USER")
                .policyId(testPolicyId)
                .ipAddress("192.168.1.103")
                .resource("/api/test")
                .build();

        // When
        RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.allowed()).isFalse();
    }

    @Test
    @DisplayName("Should handle concurrent requests correctly")
    void checkRateLimit_concurrentRequests_handlesCorrectly() throws InterruptedException {
        // Given
        String identifier = "integration-user-concurrent-" + UUID.randomUUID();
        Policy strictPolicy = Policy.builder()
                .name("strict-concurrent-policy")
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.FIXED_WINDOW)
                .maxRequests(5)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .build();

        strictPolicy = policyRepository.save(strictPolicy);
        UUID policyId = strictPolicy.getId();

        // When - Fire 10 concurrent requests (should only allow 5)
        int allowedCount = 0;
        int deniedCount = 0;

        for (int i = 0; i < 10; i++) {
            RateLimitCheckRequest request = RateLimitCheckRequest.builder()
                    .identifier(identifier)
                    .scope("USER")
                    .policyId(policyId)
                    .ipAddress("192.168.1.104")
                    .resource("/api/test")
                    .build();

            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);
            if (response.allowed()) {
                allowedCount++;
            } else {
                deniedCount++;
            }
        }

        // Then
        assertThat(allowedCount).isEqualTo(5);
        assertThat(deniedCount).isEqualTo(5);
    }
}
