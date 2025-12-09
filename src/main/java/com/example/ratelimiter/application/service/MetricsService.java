package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.MetricsResponse;
import com.example.ratelimiter.application.dto.MetricsSummary;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.RateLimitEvent;
import com.example.ratelimiter.domain.enums.IdentifierType;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.RateLimitEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for recording and querying rate limit metrics.
 *
 * Key features:
 * - Async event recording for non-blocking performance
 * - Batch insert optimization for high throughput
 * - Time-based metrics queries with flexible time ranges
 * - Aggregated summary statistics
 * - Prometheus metrics export via Micrometer
 *
 * Thread Safety: This service uses @Async methods which run in separate threads.
 * The repository operations are transactional and thread-safe.
 */
@Service
@Slf4j
public class MetricsService {

    private final RateLimitEventRepository eventRepository;
    private final PolicyRepository policyRepository;
    private final MeterRegistry meterRegistry;

    // Prometheus counters
    private final Counter allowedRequestsCounter;
    private final Counter deniedRequestsCounter;

    public MetricsService(
        RateLimitEventRepository eventRepository,
        PolicyRepository policyRepository,
        MeterRegistry meterRegistry
    ) {
        this.eventRepository = eventRepository;
        this.policyRepository = policyRepository;
        this.meterRegistry = meterRegistry;

        // Initialize Prometheus counters
        this.allowedRequestsCounter = Counter.builder("rate_limiter.requests.allowed")
            .description("Total number of allowed rate limit requests")
            .register(meterRegistry);

        this.deniedRequestsCounter = Counter.builder("rate_limiter.requests.denied")
            .description("Total number of denied rate limit requests")
            .register(meterRegistry);
    }

    /**
     * Records a single rate limit event asynchronously.
     * This method returns immediately without blocking the caller.
     *
     * @param event The rate limit event to record
     */
    @Async("metricsExecutor")
    @Transactional
    public void recordEvent(RateLimitEvent event) {
        try {
            eventRepository.save(event);

            // Update Prometheus counters
            if (event.isAllowed()) {
                allowedRequestsCounter.increment();
            } else {
                deniedRequestsCounter.increment();
            }

            log.debug("Recorded rate limit event for policy: {}, identifier: {}, allowed: {}",
                event.getPolicyId(), event.getIdentifier(), event.isAllowed());
        } catch (Exception e) {
            log.error("Failed to record rate limit event: {}", e.getMessage(), e);
            // Don't rethrow - we don't want metrics recording failures to affect the main flow
        }
    }

    /**
     * Records multiple rate limit events in a batch.
     * This is more efficient than recording events individually when dealing with bulk operations.
     * This method is also async to avoid blocking.
     *
     * @param events List of events to record
     */
    @Async("metricsExecutor")
    @Transactional
    public void recordEventsBatch(List<RateLimitEvent> events) {
        try {
            if (events == null || events.isEmpty()) {
                log.debug("No events to record in batch");
                return;
            }

            eventRepository.saveAll(events);

            // Update Prometheus counters
            long allowedCount = events.stream().filter(RateLimitEvent::isAllowed).count();
            long deniedCount = events.size() - allowedCount;

            allowedRequestsCounter.increment(allowedCount);
            deniedRequestsCounter.increment(deniedCount);

            log.debug("Recorded batch of {} rate limit events", events.size());
        } catch (Exception e) {
            log.error("Failed to record batch of rate limit events: {}", e.getMessage(), e);
            // Don't rethrow - we don't want metrics recording failures to affect the main flow
        }
    }

    /**
     * Retrieves metrics for a specific policy within a time range.
     *
     * @param policyId The policy ID to query metrics for
     * @param from Start of the time range (inclusive)
     * @param to End of the time range (exclusive)
     * @return Aggregated metrics response
     */
    @Transactional(readOnly = true)
    public MetricsResponse getMetricsByPolicyId(UUID policyId, Instant from, Instant to) {
        log.debug("Fetching metrics for policy: {} from {} to {}", policyId, from, to);

        // Convert Instant to OffsetDateTime for database query
        OffsetDateTime startTime = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
        OffsetDateTime endTime = OffsetDateTime.ofInstant(to, ZoneOffset.UTC);

        // Fetch policy for name
        Policy policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        // Query aggregated counts
        long totalRequests = eventRepository.countByPolicyIdAndTimeBetween(policyId, startTime, endTime);
        long allowedRequests = eventRepository.countAllowedByPolicyIdAndTimeBetween(policyId, startTime, endTime);
        long deniedRequests = eventRepository.countDeniedByPolicyIdAndTimeBetween(policyId, startTime, endTime);

        double denyRate = MetricsResponse.calculateDenyRate(deniedRequests, totalRequests);

        return MetricsResponse.builder()
            .policyId(policyId)
            .policyName(policy.getName())
            .totalRequests(totalRequests)
            .allowedRequests(allowedRequests)
            .deniedRequests(deniedRequests)
            .denyRate(denyRate)
            .periodStart(from)
            .periodEnd(to)
            .build();
    }

    /**
     * Retrieves aggregated summary statistics for a policy across all time.
     * Includes breakdown by identifier type (API_KEY, IP_ADDRESS, USER_ID, etc.)
     *
     * @param policyId The policy ID to query metrics for
     * @return Aggregated summary statistics
     */
    @Transactional(readOnly = true)
    public MetricsSummary getMetricsSummary(UUID policyId) {
        log.debug("Fetching metrics summary for policy: {}", policyId);

        // Verify policy exists
        policyRepository.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        // Query aggregated counts
        long totalRequests = eventRepository.countByPolicyId(policyId);
        long allowedRequests = eventRepository.countAllowedByPolicyId(policyId);
        long deniedRequests = eventRepository.countDeniedByPolicyId(policyId);

        // Query breakdown by identifier type
        Map<String, Long> requestsByIdentifierType = getRequestsByIdentifierType(policyId);

        return MetricsSummary.calculate(
            totalRequests,
            allowedRequests,
            deniedRequests,
            requestsByIdentifierType
        );
    }

    /**
     * Helper method to aggregate requests by identifier type.
     *
     * @param policyId The policy ID to query
     * @return Map of identifier type to request count
     */
    private Map<String, Long> getRequestsByIdentifierType(UUID policyId) {
        List<Object[]> results = eventRepository.countByPolicyIdGroupedByIdentifierType(policyId);
        Map<String, Long> breakdown = new HashMap<>();

        for (Object[] result : results) {
            IdentifierType type = (IdentifierType) result[0];
            Long count = (Long) result[1];
            breakdown.put(type.name(), count);
        }

        return breakdown;
    }

    /**
     * Records a gauge metric for current rate limit usage.
     * This can be called periodically to track real-time usage.
     *
     * @param policyId The policy ID
     * @param identifier The identifier being rate limited
     * @param current Current count
     * @param limit Maximum limit
     */
    public void recordUsageGauge(UUID policyId, String identifier, long current, long limit) {
        try {
            meterRegistry.gauge("rate_limiter.usage",
                List.of(
                    io.micrometer.core.instrument.Tag.of("policy_id", policyId.toString()),
                    io.micrometer.core.instrument.Tag.of("identifier", identifier)
                ),
                current
            );

            meterRegistry.gauge("rate_limiter.limit",
                List.of(
                    io.micrometer.core.instrument.Tag.of("policy_id", policyId.toString()),
                    io.micrometer.core.instrument.Tag.of("identifier", identifier)
                ),
                limit
            );
        } catch (Exception e) {
            log.error("Failed to record usage gauge: {}", e.getMessage(), e);
        }
    }
}
