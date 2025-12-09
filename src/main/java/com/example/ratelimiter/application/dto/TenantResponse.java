package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.enums.TenantTier;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TenantResponse(
        UUID id,
        String name,
        TenantTier tier,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TenantResponse from(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .tier(tenant.getTier())
                .enabled(tenant.isEnabled())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
