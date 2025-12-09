package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.entity.PolicyRule;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PolicyRuleResponse(
        UUID id,
        UUID policyId,
        String policyName,
        String name,
        String resourcePattern,
        String httpMethods,
        Integer priority,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PolicyRuleResponse from(PolicyRule policyRule) {
        return PolicyRuleResponse.builder()
                .id(policyRule.getId())
                .policyId(policyRule.getPolicy() != null ? policyRule.getPolicy().getId() : null)
                .policyName(policyRule.getPolicy() != null ? policyRule.getPolicy().getName() : null)
                .name(policyRule.getName())
                .resourcePattern(policyRule.getResourcePattern())
                .httpMethods(policyRule.getHttpMethods())
                .priority(policyRule.getPriority())
                .enabled(policyRule.isEnabled())
                .createdAt(policyRule.getCreatedAt())
                .updatedAt(policyRule.getUpdatedAt())
                .build();
    }
}
