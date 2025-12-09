package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.MetricsResponse;
import com.example.ratelimiter.application.dto.MetricsSummary;
import com.example.ratelimiter.application.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * REST controller for metrics queries.
 *
 * Provides endpoints to retrieve rate limit metrics and statistics for monitoring
 * and analytics purposes. All timestamps use ISO 8601 format.
 *
 * Endpoints:
 * - GET /api/policies/{id}/metrics - Get time-ranged metrics for a policy
 * - GET /api/policies/{id}/metrics/summary - Get aggregated summary for a policy
 */
@RestController
@RequestMapping("/api/policies/{policyId}/metrics")
@RequiredArgsConstructor
@Slf4j
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * Get metrics for a specific policy within a time range.
     *
     * Query parameters:
     * - from: Start time (ISO 8601 format, e.g., "2025-01-01T00:00:00Z"). Defaults to 24 hours ago.
     * - to: End time (ISO 8601 format, e.g., "2025-01-02T00:00:00Z"). Defaults to now.
     *
     * Example request:
     * GET /api/policies/{policyId}/metrics?from=2025-01-01T00:00:00Z&to=2025-01-02T00:00:00Z
     *
     * Example response:
     * {
     *   "policyId": "123e4567-e89b-12d3-a456-426614174000",
     *   "policyName": "API Rate Limit",
     *   "totalRequests": 1500,
     *   "allowedRequests": 1400,
     *   "deniedRequests": 100,
     *   "denyRate": 6.67,
     *   "periodStart": "2025-01-01T00:00:00Z",
     *   "periodEnd": "2025-01-02T00:00:00Z"
     * }
     *
     * @param policyId The policy UUID
     * @param from Start of the time range (defaults to 24 hours ago)
     * @param to End of the time range (defaults to now)
     * @return Metrics response with aggregated statistics
     */
    @GetMapping
    public ResponseEntity<MetricsResponse> getMetrics(
        @PathVariable UUID policyId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to
    ) {
        log.info("Fetching metrics for policy: {} from {} to {}", policyId, from, to);

        // Default time range: last 24 hours
        Instant endTime = (to != null) ? to : Instant.now();
        Instant startTime = (from != null) ? from : endTime.minus(24, ChronoUnit.HOURS);

        // Validate time range
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }

        // Limit query range to prevent performance issues (e.g., max 90 days)
        long daysBetween = ChronoUnit.DAYS.between(startTime, endTime);
        if (daysBetween > 90) {
            throw new IllegalArgumentException("Time range cannot exceed 90 days");
        }

        try {
            MetricsResponse metrics = metricsService.getMetricsByPolicyId(policyId, startTime, endTime);
            return ResponseEntity.ok(metrics);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching metrics for policy {}: {}", policyId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch metrics", e);
        }
    }

    /**
     * Get aggregated summary statistics for a specific policy (all time).
     *
     * This endpoint provides a high-level overview of rate limiting activity
     * including total requests, allow/deny rates, and breakdown by identifier type.
     *
     * Example request:
     * GET /api/policies/{policyId}/metrics/summary
     *
     * Example response:
     * {
     *   "totalRequests": 15000,
     *   "allowedRequests": 14500,
     *   "deniedRequests": 500,
     *   "allowRate": 96.67,
     *   "denyRate": 3.33,
     *   "requestsByIdentifierType": {
     *     "API_KEY": 10000,
     *     "IP_ADDRESS": 5000
     *   }
     * }
     *
     * @param policyId The policy UUID
     * @return Aggregated summary statistics
     */
    @GetMapping("/summary")
    public ResponseEntity<MetricsSummary> getMetricsSummary(@PathVariable UUID policyId) {
        log.info("Fetching metrics summary for policy: {}", policyId);

        try {
            MetricsSummary summary = metricsService.getMetricsSummary(policyId);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching metrics summary for policy {}: {}", policyId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch metrics summary", e);
        }
    }
}
