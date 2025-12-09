package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.entity.UserPolicy;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record UserPolicyResponse(
        UUID id,
        String userId,
        UUID policyId,
        String policyName,
        UUID tenantId,
        String tenantName,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static UserPolicyResponse from(UserPolicy userPolicy) {
        return UserPolicyResponse.builder()
                .id(userPolicy.getId())
                .userId(userPolicy.getUserId())
                .policyId(userPolicy.getPolicy() != null ? userPolicy.getPolicy().getId() : null)
                .policyName(userPolicy.getPolicy() != null ? userPolicy.getPolicy().getName() : null)
                .tenantId(userPolicy.getTenant() != null ? userPolicy.getTenant().getId() : null)
                .tenantName(userPolicy.getTenant() != null ? userPolicy.getTenant().getName() : null)
                .enabled(userPolicy.isEnabled())
                .createdAt(userPolicy.getCreatedAt())
                .updatedAt(userPolicy.getUpdatedAt())
                .build();
    }
}
