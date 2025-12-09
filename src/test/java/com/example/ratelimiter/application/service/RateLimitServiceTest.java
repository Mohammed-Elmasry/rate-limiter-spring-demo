package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.RateLimitCheckRequest;
import com.example.ratelimiter.application.dto.RateLimitCheckResponse;
import com.example.ratelimiter.domain.algorithm.AlgorithmFactory;
import com.example.ratelimiter.domain.algorithm.RateLimitAlgorithm;
import com.example.ratelimiter.domain.algorithm.RateLimitResult;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.infrastructure.resilience.FallbackHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for RateLimitService.
 *
 * Test Strategy:
 * - Test policy resolution hierarchy (explicit > IP > tenant > global)
 * - Test rate limit check logic (allowed/denied)
 * - Test error handling and fail modes
 * - Test metrics recording (async, non-blocking)
 * - Test circuit breaker fallback scenarios
 * - Test edge cases (null values, disabled policies)
 *
 * Coverage Goals:
 * - All policy resolution paths
 * - Both allowed and denied outcomes
 * - Both fail modes (FAIL_OPEN, FAIL_CLOSED)
 * - Error scenarios with graceful degradation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService Tests")
class RateLimitServiceTest {

    @Mock
    private AlgorithmFactory algorithmFactory;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private IpRuleService ipRuleService;

    @Mock
    private MetricsService metricsService;

    @Mock
    private FallbackHandler fallbackHandler;

    @Mock
    private RateLimitAlgorithm mockAlgorithm;

    @InjectMocks
    private RateLimitService rateLimitService;

    private Policy testPolicy;
    private UUID testPolicyId;
    private UUID testTenantId;

    @BeforeEach
    void setUp() {
        testPolicyId = UUID.randomUUID();
        testTenantId = UUID.randomUUID();

        testPolicy = Policy.builder()
                .id(testPolicyId)
                .name("test-policy")
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .isDefault(false)
                .build();
    }

    @Nested
    @DisplayName("Policy Resolution Tests")
    class PolicyResolutionTests {

        @Test
        @DisplayName("Should resolve explicit policy when policyId is provided")
        void resolvePolicyById_explicitPolicyId_returnsPolicy() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(50, 30);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute(anyString(), any(Policy.class))).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            assertThat(response.remaining()).isEqualTo(50);
            assertThat(response.policyId()).isEqualTo(testPolicyId);

            verify(policyRepository).findById(testPolicyId);
            verify(ipRuleService, never()).getPolicyForIp(anyString(), any());
            verify(policyRepository, never()).findDefaultByTenantId(any());
            verify(policyRepository, never()).findDefaultByScope(any());
        }

        @Test
        @DisplayName("Should resolve IP-specific policy when no explicit policyId")
        void resolvePolicyByIp_noExplicitPolicyId_returnsIpPolicy() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    null,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(50, 30);

            when(ipRuleService.getPolicyForIp("192.168.1.1", testTenantId))
                    .thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute(anyString(), any(Policy.class))).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            verify(ipRuleService).getPolicyForIp("192.168.1.1", testTenantId);
            verify(policyRepository, never()).findDefaultByTenantId(any());
        }

        @Test
        @DisplayName("Should resolve tenant default policy as fallback")
        void resolveTenantDefaultPolicy_noExplicitOrIpPolicy_returnsTenantPolicy() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    null,
                    testTenantId,
                    null,
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(50, 30);

            when(policyRepository.findDefaultByTenantId(testTenantId))
                    .thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute(anyString(), any(Policy.class))).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            verify(policyRepository).findDefaultByTenantId(testTenantId);
            verify(policyRepository, never()).findDefaultByScope(PolicyScope.GLOBAL);
        }

        @Test
        @DisplayName("Should resolve global default policy as last resort")
        void resolveGlobalDefaultPolicy_noOtherPolicies_returnsGlobalPolicy() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    null,
                    null,
                    null,
                    "/api/test",
                    "USER"
            );

            Policy globalPolicy = Policy.builder()
                    .id(UUID.randomUUID())
                    .name("global-policy")
                    .scope(PolicyScope.GLOBAL)
                    .algorithm(Algorithm.FIXED_WINDOW)
                    .maxRequests(1000)
                    .windowSeconds(3600)
                    .failMode(FailMode.FAIL_CLOSED)
                    .enabled(true)
                    .isDefault(true)
                    .build();

            RateLimitResult algorithmResult = RateLimitResult.allowed(500, 1800);

            when(policyRepository.findDefaultByScope(PolicyScope.GLOBAL))
                    .thenReturn(Optional.of(globalPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.FIXED_WINDOW)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute(anyString(), any(Policy.class))).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            assertThat(response.policyId()).isEqualTo(globalPolicy.getId());
            verify(policyRepository).findDefaultByScope(PolicyScope.GLOBAL);
        }

        @Test
        @DisplayName("Should deny request when no policy found")
        void noPolicyFound_noApplicablePolicy_returnsDenied() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    null,
                    null,
                    null,
                    "/api/test",
                    "USER"
            );

            when(policyRepository.findDefaultByScope(PolicyScope.GLOBAL))
                    .thenReturn(Optional.empty());

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isFalse();
            assertThat(response.policyId()).isNull();
            assertThat(response.algorithm()).isEqualTo("NONE");
        }
    }

    @Nested
    @DisplayName("Rate Limit Check Logic Tests")
    class RateLimitCheckTests {

        @Test
        @DisplayName("Should allow request when within rate limit")
        void checkRateLimit_withinLimit_returnsAllowed() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(99, 60);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute("user123", testPolicy)).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            assertThat(response.remaining()).isEqualTo(99);
            assertThat(response.limit()).isEqualTo(100);
            assertThat(response.resetInSeconds()).isEqualTo(60);
            assertThat(response.policyId()).isEqualTo(testPolicyId);
            assertThat(response.algorithm()).isEqualTo("TOKEN_BUCKET");

            verify(mockAlgorithm).execute("user123", testPolicy);
            verify(metricsService).recordEvent(any());
        }

        @Test
        @DisplayName("Should deny request when rate limit exceeded")
        void checkRateLimit_limitExceeded_returnsDenied() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.denied(0, 60, 60);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute("user123", testPolicy)).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isFalse();
            assertThat(response.remaining()).isEqualTo(0);
            assertThat(response.limit()).isEqualTo(100);
            assertThat(response.resetInSeconds()).isEqualTo(60);

            verify(mockAlgorithm).execute("user123", testPolicy);
            verify(metricsService).recordEvent(any());
        }

        @Test
        @DisplayName("Should deny request when policy is disabled")
        void checkRateLimit_policyDisabled_returnsDenied() {
            // Given
            testPolicy.setEnabled(false);

            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isFalse();
            assertThat(response.remaining()).isEqualTo(0);

            verify(algorithmFactory, never()).getAlgorithm(any());
            verify(mockAlgorithm, never()).execute(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should fail closed when error occurs and policy is FAIL_CLOSED")
        void handleError_failClosedPolicy_returnsDenied() {
            // Given
            testPolicy.setFailMode(FailMode.FAIL_CLOSED);

            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute(anyString(), any())).thenThrow(new RuntimeException("Redis error"));

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isFalse();
        }

        @Test
        @DisplayName("Should fail open when error occurs and policy is FAIL_OPEN")
        void handleError_failOpenPolicy_returnsAllowed() {
            // Given
            testPolicy.setFailMode(FailMode.FAIL_OPEN);

            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute(anyString(), any())).thenThrow(new RuntimeException("Redis error"));

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            assertThat(response.remaining()).isEqualTo(100);
            assertThat(response.limit()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should fail closed when no policy found and error occurs")
        void handleError_noPolicyFound_failsClosed() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    null,
                    null,
                    null,
                    "/api/test",
                    "USER"
            );

            when(policyRepository.findDefaultByScope(PolicyScope.GLOBAL))
                    .thenReturn(Optional.empty());

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isFalse();
            assertThat(response.algorithm()).isEqualTo("NONE");
        }
    }

    @Nested
    @DisplayName("Metrics Recording Tests")
    class MetricsRecordingTests {

        @Test
        @DisplayName("Should record metrics asynchronously for allowed request")
        void recordMetrics_allowedRequest_callsMetricsService() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(99, 60);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute("user123", testPolicy)).thenReturn(algorithmResult);

            // When
            rateLimitService.checkRateLimit(request);

            // Then
            verify(metricsService).recordEvent(any());
            verify(metricsService).recordUsageGauge(
                    eq(testPolicyId),
                    eq("user123"),
                    eq(1L), // current usage: maxRequests - remaining
                    eq(100L) // limit
            );
        }

        @Test
        @DisplayName("Should record metrics asynchronously for denied request")
        void recordMetrics_deniedRequest_callsMetricsService() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.denied(0, 60, 60);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute("user123", testPolicy)).thenReturn(algorithmResult);

            // When
            rateLimitService.checkRateLimit(request);

            // Then
            verify(metricsService).recordEvent(any());
            verify(metricsService).recordUsageGauge(
                    eq(testPolicyId),
                    eq("user123"),
                    eq(100L), // current usage: maxRequests - remaining
                    eq(100L) // limit
            );
        }

        @Test
        @DisplayName("Should not fail rate limit check if metrics recording fails")
        void recordMetrics_metricsServiceThrowsException_doesNotAffectResponse() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(99, 60);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute("user123", testPolicy)).thenReturn(algorithmResult);
            doThrow(new RuntimeException("Metrics service error")).when(metricsService).recordEvent(any());

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            verify(metricsService).recordEvent(any());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null IP address gracefully")
        void checkRateLimit_nullIpAddress_handlesGracefully() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    null,
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(99, 60);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute("user123", testPolicy)).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            verify(ipRuleService, never()).getPolicyForIp(anyString(), any());
        }

        @Test
        @DisplayName("Should handle empty IP address gracefully")
        void checkRateLimit_emptyIpAddress_handlesGracefully() {
            // Given
            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(99, 60);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute("user123", testPolicy)).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            verify(ipRuleService, never()).getPolicyForIp(anyString(), any());
        }

        @Test
        @DisplayName("Should handle policy with tenant association")
        void checkRateLimit_policyWithTenant_worksCorrectly() {
            // Given
            Tenant tenant = Tenant.builder()
                    .id(testTenantId)
                    .name("test-tenant")
                    .build();

            testPolicy.setTenant(tenant);

            RateLimitCheckRequest request = new RateLimitCheckRequest(
                    "user123",
                    testPolicyId,
                    testTenantId,
                    "192.168.1.1",
                    "/api/test",
                    "USER"
            );

            RateLimitResult algorithmResult = RateLimitResult.allowed(99, 60);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET)).thenReturn(mockAlgorithm);
            when(mockAlgorithm.execute("user123", testPolicy)).thenReturn(algorithmResult);

            // When
            RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.allowed()).isTrue();
            assertThat(response.policyId()).isEqualTo(testPolicyId);
        }
    }
}
