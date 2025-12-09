package com.example.ratelimiter.application.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ApiKeyCreatedResponse(
        UUID id,
        String apiKey,
        String keyPrefix,
        String name,
        UUID tenantId,
        String tenantName,
        UUID policyId,
        String policyName,
        boolean enabled,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        String warning
) {
    public ApiKeyCreatedResponse {
        if (warning == null) {
            warning = "Store this API key securely. It cannot be retrieved again.";
        }
    }
}
