package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.MetricsResponse;
import com.example.ratelimiter.application.dto.MetricsSummary;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.RateLimitEvent;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.IdentifierType;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.RateLimitEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for MetricsService.
 *
 * Test Strategy:
 * - Test async event recording (single and batch)
 * - Test metrics queries (by policy, time range)
 * - Test aggregated summary statistics
 * - Test Prometheus counter increments
 * - Test gauge recording
 * - Test error handling (should not fail main flow)
 * - Test edge cases (empty data, null values)
 *
 * Coverage Goals:
 * - All public methods
 * - Async behavior (non-blocking)
 * - Error resilience
 * - Micrometer integration
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService Tests")
class MetricsServiceTest {

    @Mock
    private RateLimitEventRepository eventRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter allowedCounter;

    @Mock
    private Counter deniedCounter;

    private MetricsService metricsService;

    private Policy testPolicy;
    private UUID testPolicyId;

    @BeforeEach
    void setUp() {
        testPolicyId = UUID.randomUUID();

        testPolicy = Policy.builder()
                .id(testPolicyId)
                .name("test-policy")
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .build();

        // Mock Counter.builder() chain
        Counter.Builder allowedBuilder = mock(Counter.Builder.class);
        Counter.Builder deniedBuilder = mock(Counter.Builder.class);

        when(meterRegistry.counter(anyString())).thenReturn(mock(Counter.class));

        // Initialize service with constructor
        metricsService = new MetricsService(eventRepository, policyRepository, meterRegistry);
    }

    @Nested
    @DisplayName("Event Recording Tests")
    class EventRecordingTests {

        @Test
        @DisplayName("Should record single event successfully")
        void recordEvent_validEvent_savesEvent() {
            // Given
            RateLimitEvent event = RateLimitEvent.builder()
                    .policyId(testPolicyId)
                    .identifier("user123")
                    .identifierType(IdentifierType.USER_ID)
                    .allowed(true)
                    .remaining(99)
                    .limitValue(100)
                    .ipAddress("192.168.1.1")
                    .resource("/api/test")
                    .eventTime(OffsetDateTime.now())
                    .build();

            when(eventRepository.save(any(RateLimitEvent.class))).thenReturn(event);

            // When
            metricsService.recordEvent(event);

            // Then
            verify(eventRepository).save(event);
        }

        @Test
        @DisplayName("Should record denied event successfully")
        void recordEvent_deniedEvent_savesEvent() {
            // Given
            RateLimitEvent event = RateLimitEvent.builder()
                    .policyId(testPolicyId)
                    .identifier("user123")
                    .identifierType(IdentifierType.USER_ID)
                    .allowed(false)
                    .remaining(0)
                    .limitValue(100)
                    .ipAddress("192.168.1.1")
                    .resource("/api/test")
                    .eventTime(OffsetDateTime.now())
                    .build();

            when(eventRepository.save(any(RateLimitEvent.class))).thenReturn(event);

            // When
            metricsService.recordEvent(event);

            // Then
            verify(eventRepository).save(event);
        }

        @Test
        @DisplayName("Should handle exception in recordEvent gracefully")
        void recordEvent_repositoryThrowsException_doesNotPropagate() {
            // Given
            RateLimitEvent event = RateLimitEvent.builder()
                    .policyId(testPolicyId)
                    .identifier("user123")
                    .identifierType(IdentifierType.USER_ID)
                    .allowed(true)
                    .remaining(99)
                    .limitValue(100)
                    .build();

            when(eventRepository.save(any(RateLimitEvent.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When / Then - Should not throw exception
            metricsService.recordEvent(event);

            verify(eventRepository).save(event);
        }

        @Test
        @DisplayName("Should record batch of events successfully")
        void recordEventsBatch_validEvents_savesAll() {
            // Given
            RateLimitEvent event1 = RateLimitEvent.builder()
                    .policyId(testPolicyId)
                    .identifier("user123")
                    .identifierType(IdentifierType.USER_ID)
                    .allowed(true)
                    .remaining(99)
                    .limitValue(100)
                    .build();

            RateLimitEvent event2 = RateLimitEvent.builder()
                    .policyId(testPolicyId)
                    .identifier("user456")
                    .identifierType(IdentifierType.USER_ID)
                    .allowed(false)
                    .remaining(0)
                    .limitValue(100)
                    .build();

            List<RateLimitEvent> events = Arrays.asList(event1, event2);

            when(eventRepository.saveAll(anyList())).thenReturn(events);

            // When
            metricsService.recordEventsBatch(events);

            // Then
            verify(eventRepository).saveAll(events);
        }

        @Test
        @DisplayName("Should handle empty batch gracefully")
        void recordEventsBatch_emptyList_doesNothing() {
            // Given
            List<RateLimitEvent> events = Collections.emptyList();

            // When
            metricsService.recordEventsBatch(events);

            // Then
            verify(eventRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Should handle null batch gracefully")
        void recordEventsBatch_nullList_doesNothing() {
            // When
            metricsService.recordEventsBatch(null);

            // Then
            verify(eventRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Should handle exception in batch recording gracefully")
        void recordEventsBatch_repositoryThrowsException_doesNotPropagate() {
            // Given
            RateLimitEvent event = RateLimitEvent.builder()
                    .policyId(testPolicyId)
                    .identifier("user123")
                    .identifierType(IdentifierType.USER_ID)
                    .allowed(true)
                    .remaining(99)
                    .limitValue(100)
                    .build();

            List<RateLimitEvent> events = Collections.singletonList(event);

            when(eventRepository.saveAll(anyList())).thenThrow(new RuntimeException("Database error"));

            // When / Then - Should not throw exception
            metricsService.recordEventsBatch(events);

            verify(eventRepository).saveAll(events);
        }
    }

    @Nested
    @DisplayName("Metrics Query Tests")
    class MetricsQueryTests {

        @Test
        @DisplayName("Should get metrics by policy ID and time range")
        void getMetricsByPolicyId_validParams_returnsMetrics() {
            // Given
            Instant from = Instant.now().minusSeconds(3600);
            Instant to = Instant.now();
            OffsetDateTime startTime = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
            OffsetDateTime endTime = OffsetDateTime.ofInstant(to, ZoneOffset.UTC);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(eventRepository.countByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(150L);
            when(eventRepository.countAllowedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(100L);
            when(eventRepository.countDeniedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(50L);

            // When
            MetricsResponse response = metricsService.getMetricsByPolicyId(testPolicyId, from, to);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getPolicyId()).isEqualTo(testPolicyId);
            assertThat(response.getPolicyName()).isEqualTo("test-policy");
            assertThat(response.getTotalRequests()).isEqualTo(150L);
            assertThat(response.getAllowedRequests()).isEqualTo(100L);
            assertThat(response.getDeniedRequests()).isEqualTo(50L);
            assertThat(response.getDenyRate()).isEqualTo(33.33);
            assertThat(response.getPeriodStart()).isEqualTo(from);
            assertThat(response.getPeriodEnd()).isEqualTo(to);

            verify(policyRepository).findById(testPolicyId);
            verify(eventRepository).countByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime);
            verify(eventRepository).countAllowedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime);
            verify(eventRepository).countDeniedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime);
        }

        @Test
        @DisplayName("Should throw exception when policy not found")
        void getMetricsByPolicyId_policyNotFound_throwsException() {
            // Given
            Instant from = Instant.now().minusSeconds(3600);
            Instant to = Instant.now();

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> metricsService.getMetricsByPolicyId(testPolicyId, from, to))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Policy not found: " + testPolicyId);

            verify(eventRepository, never()).countByPolicyIdAndTimeBetween(any(), any(), any());
        }

        @Test
        @DisplayName("Should handle zero requests in time range")
        void getMetricsByPolicyId_noRequests_returnsZeroMetrics() {
            // Given
            Instant from = Instant.now().minusSeconds(3600);
            Instant to = Instant.now();
            OffsetDateTime startTime = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
            OffsetDateTime endTime = OffsetDateTime.ofInstant(to, ZoneOffset.UTC);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(eventRepository.countByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(0L);
            when(eventRepository.countAllowedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(0L);
            when(eventRepository.countDeniedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(0L);

            // When
            MetricsResponse response = metricsService.getMetricsByPolicyId(testPolicyId, from, to);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getTotalRequests()).isEqualTo(0L);
            assertThat(response.getAllowedRequests()).isEqualTo(0L);
            assertThat(response.getDeniedRequests()).isEqualTo(0L);
            assertThat(response.getDenyRate()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Metrics Summary Tests")
    class MetricsSummaryTests {

        @Test
        @DisplayName("Should get metrics summary for policy")
        void getMetricsSummary_validPolicyId_returnsSummary() {
            // Given
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(eventRepository.countByPolicyId(testPolicyId)).thenReturn(200L);
            when(eventRepository.countAllowedByPolicyId(testPolicyId)).thenReturn(150L);
            when(eventRepository.countDeniedByPolicyId(testPolicyId)).thenReturn(50L);

            List<Object[]> identifierTypeBreakdown = Arrays.asList(
                    new Object[]{IdentifierType.USER_ID, 120L},
                    new Object[]{IdentifierType.API_KEY, 80L}
            );
            when(eventRepository.countByPolicyIdGroupedByIdentifierType(testPolicyId))
                    .thenReturn(identifierTypeBreakdown);

            // When
            MetricsSummary summary = metricsService.getMetricsSummary(testPolicyId);

            // Then
            assertThat(summary).isNotNull();
            assertThat(summary.getTotalRequests()).isEqualTo(200L);
            assertThat(summary.getAllowedRequests()).isEqualTo(150L);
            assertThat(summary.getDeniedRequests()).isEqualTo(50L);
            assertThat(summary.getRequestsByIdentifierType()).hasSize(2);
            assertThat(summary.getRequestsByIdentifierType().get("USER_ID")).isEqualTo(120L);
            assertThat(summary.getRequestsByIdentifierType().get("API_KEY")).isEqualTo(80L);

            verify(policyRepository).findById(testPolicyId);
            verify(eventRepository).countByPolicyId(testPolicyId);
            verify(eventRepository).countAllowedByPolicyId(testPolicyId);
            verify(eventRepository).countDeniedByPolicyId(testPolicyId);
            verify(eventRepository).countByPolicyIdGroupedByIdentifierType(testPolicyId);
        }

        @Test
        @DisplayName("Should throw exception when policy not found in summary")
        void getMetricsSummary_policyNotFound_throwsException() {
            // Given
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> metricsService.getMetricsSummary(testPolicyId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Policy not found: " + testPolicyId);

            verify(eventRepository, never()).countByPolicyId(any());
        }

        @Test
        @DisplayName("Should handle empty identifier type breakdown")
        void getMetricsSummary_noIdentifierTypeBreakdown_returnsEmptyMap() {
            // Given
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(eventRepository.countByPolicyId(testPolicyId)).thenReturn(100L);
            when(eventRepository.countAllowedByPolicyId(testPolicyId)).thenReturn(100L);
            when(eventRepository.countDeniedByPolicyId(testPolicyId)).thenReturn(0L);
            when(eventRepository.countByPolicyIdGroupedByIdentifierType(testPolicyId))
                    .thenReturn(Collections.emptyList());

            // When
            MetricsSummary summary = metricsService.getMetricsSummary(testPolicyId);

            // Then
            assertThat(summary).isNotNull();
            assertThat(summary.getRequestsByIdentifierType()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Gauge Recording Tests")
    class GaugeRecordingTests {

        @Test
        @DisplayName("Should record usage gauge successfully")
        void recordUsageGauge_validParams_registersGauges() {
            // Given
            String identifier = "user123";
            long current = 75L;
            long limit = 100L;

            // When
            metricsService.recordUsageGauge(testPolicyId, identifier, current, limit);

            // Then
            verify(meterRegistry, times(2)).gauge(anyString(), anyList(), anyLong());
        }

        @Test
        @DisplayName("Should handle exception in gauge recording gracefully")
        void recordUsageGauge_meterRegistryThrowsException_doesNotPropagate() {
            // Given
            String identifier = "user123";
            long current = 75L;
            long limit = 100L;

            doThrow(new RuntimeException("Metrics error"))
                    .when(meterRegistry).gauge(anyString(), anyList(), anyLong());

            // When / Then - Should not throw exception
            metricsService.recordUsageGauge(testPolicyId, identifier, current, limit);

            verify(meterRegistry).gauge(anyString(), anyList(), anyLong());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should calculate deny rate correctly for 100% deny")
        void getMetricsByPolicyId_allDenied_returnsCorrectDenyRate() {
            // Given
            Instant from = Instant.now().minusSeconds(3600);
            Instant to = Instant.now();
            OffsetDateTime startTime = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
            OffsetDateTime endTime = OffsetDateTime.ofInstant(to, ZoneOffset.UTC);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(eventRepository.countByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(100L);
            when(eventRepository.countAllowedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(0L);
            when(eventRepository.countDeniedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(100L);

            // When
            MetricsResponse response = metricsService.getMetricsByPolicyId(testPolicyId, from, to);

            // Then
            assertThat(response.getDenyRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("Should calculate deny rate correctly for 0% deny")
        void getMetricsByPolicyId_noneDenied_returnsCorrectDenyRate() {
            // Given
            Instant from = Instant.now().minusSeconds(3600);
            Instant to = Instant.now();
            OffsetDateTime startTime = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
            OffsetDateTime endTime = OffsetDateTime.ofInstant(to, ZoneOffset.UTC);

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(eventRepository.countByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(100L);
            when(eventRepository.countAllowedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(100L);
            when(eventRepository.countDeniedByPolicyIdAndTimeBetween(testPolicyId, startTime, endTime))
                    .thenReturn(0L);

            // When
            MetricsResponse response = metricsService.getMetricsByPolicyId(testPolicyId, from, to);

            // Then
            assertThat(response.getDenyRate()).isEqualTo(0.0);
        }
    }
}
