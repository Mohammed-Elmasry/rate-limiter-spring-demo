package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PolicyResponse(
        UUID id,
        String name,
        String description,
        PolicyScope scope,
        Algorithm algorithm,
        Integer maxRequests,
        Integer windowSeconds,
        Integer burstCapacity,
        BigDecimal refillRate,
        FailMode failMode,
        boolean enabled,
        boolean isDefault,
        UUID tenantId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PolicyResponse from(Policy policy) {
        return PolicyResponse.builder()
                .id(policy.getId())
                .name(policy.getName())
                .description(policy.getDescription())
                .scope(policy.getScope())
                .algorithm(policy.getAlgorithm())
                .maxRequests(policy.getMaxRequests())
                .windowSeconds(policy.getWindowSeconds())
                .burstCapacity(policy.getBurstCapacity())
                .refillRate(policy.getRefillRate())
                .failMode(policy.getFailMode())
                .enabled(policy.isEnabled())
                .isDefault(policy.isDefault())
                .tenantId(policy.getTenant() != null ? policy.getTenant().getId() : null)
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
