package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.entity.ApiKey;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ApiKeyResponse(
        UUID id,
        String keyPrefix,
        String name,
        UUID tenantId,
        String tenantName,
        UUID policyId,
        String policyName,
        boolean enabled,
        OffsetDateTime expiresAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ApiKeyResponse from(ApiKey apiKey) {
        return ApiKeyResponse.builder()
                .id(apiKey.getId())
                .keyPrefix(apiKey.getKeyPrefix())
                .name(apiKey.getName())
                .tenantId(apiKey.getTenant() != null ? apiKey.getTenant().getId() : null)
                .tenantName(apiKey.getTenant() != null ? apiKey.getTenant().getName() : null)
                .policyId(apiKey.getPolicy() != null ? apiKey.getPolicy().getId() : null)
                .policyName(apiKey.getPolicy() != null ? apiKey.getPolicy().getName() : null)
                .enabled(apiKey.isEnabled())
                .expiresAt(apiKey.getExpiresAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .build();
    }
}
