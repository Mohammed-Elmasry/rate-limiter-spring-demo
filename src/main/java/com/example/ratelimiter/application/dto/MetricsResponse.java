package com.example.ratelimiter.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for metrics data for a specific policy and time period.
 * Provides aggregated statistics on allowed and denied requests.
 */
@Builder
public record MetricsResponse(
    UUID policyId,
    String policyName,
    long totalRequests,
    long allowedRequests,
    long deniedRequests,
    double denyRate,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    Instant periodStart,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    Instant periodEnd
) {
    /**
     * Calculate deny rate from counts.
     * Handles division by zero safely.
     */
    public static double calculateDenyRate(long deniedRequests, long totalRequests) {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) deniedRequests / totalRequests * 100.0;
    }
}
