package com.example.ratelimiter.application.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record RateLimitCheckResponse(
        boolean allowed,
        long remaining,
        long limit,
        long resetInSeconds,
        UUID policyId,
        String algorithm,
        Long retryAfter
) {
    public static RateLimitCheckResponse allowed(long remaining, long limit, long resetInSeconds, UUID policyId, String algorithm) {
        return RateLimitCheckResponse.builder()
                .allowed(true)
                .remaining(remaining)
                .limit(limit)
                .resetInSeconds(resetInSeconds)
                .policyId(policyId)
                .algorithm(algorithm)
                .retryAfter(null)
                .build();
    }

    public static RateLimitCheckResponse denied(long remaining, long limit, long resetInSeconds, UUID policyId, String algorithm) {
        return RateLimitCheckResponse.builder()
                .allowed(false)
                .remaining(remaining)
                .limit(limit)
                .resetInSeconds(resetInSeconds)
                .policyId(policyId)
                .algorithm(algorithm)
                .retryAfter(resetInSeconds)
                .build();
    }
}
