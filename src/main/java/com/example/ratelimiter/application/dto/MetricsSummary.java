package com.example.ratelimiter.application.dto;

import lombok.Builder;

import java.util.Map;

/**
 * Summary statistics for rate limit events.
 * Provides aggregated view of request patterns including breakdowns by identifier type.
 */
@Builder
public record MetricsSummary(
    long totalRequests,
    long allowedRequests,
    long deniedRequests,
    double allowRate,
    double denyRate,
    Map<String, Long> requestsByIdentifierType
) {
    /**
     * Calculate allow and deny rates from counts.
     */
    public static MetricsSummary calculate(
        long totalRequests,
        long allowedRequests,
        long deniedRequests,
        Map<String, Long> requestsByIdentifierType
    ) {
        double allowRate = 0.0;
        double denyRate = 0.0;

        if (totalRequests > 0) {
            allowRate = (double) allowedRequests / totalRequests * 100.0;
            denyRate = (double) deniedRequests / totalRequests * 100.0;
        }

        return MetricsSummary.builder()
            .totalRequests(totalRequests)
            .allowedRequests(allowedRequests)
            .deniedRequests(deniedRequests)
            .allowRate(allowRate)
            .denyRate(denyRate)
            .requestsByIdentifierType(requestsByIdentifierType)
            .build();
    }
}
