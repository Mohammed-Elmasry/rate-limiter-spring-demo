package com.example.ratelimiter.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record RateLimitCheckRequest(
        @NotBlank(message = "Identifier is required")
        String identifier,

        @NotNull(message = "Scope is required")
        String scope,

        String resource,

        String method,

        UUID tenantId,

        UUID policyId,

        String ipAddress,

        Integer requestedTokens
) {
    public RateLimitCheckRequest {
        if (requestedTokens == null || requestedTokens < 1) {
            requestedTokens = 1;
        }
    }
}
