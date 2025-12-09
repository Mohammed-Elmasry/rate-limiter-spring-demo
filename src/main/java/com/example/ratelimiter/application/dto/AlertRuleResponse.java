package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.entity.AlertRule;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record AlertRuleResponse(
        UUID id,
        String name,
        UUID policyId,
        String policyName,
        Integer thresholdPercentage,
        Integer windowSeconds,
        Integer cooldownSeconds,
        boolean enabled,
        OffsetDateTime lastTriggeredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AlertRuleResponse from(AlertRule alertRule) {
        return AlertRuleResponse.builder()
                .id(alertRule.getId())
                .name(alertRule.getName())
                .policyId(alertRule.getPolicy() != null ? alertRule.getPolicy().getId() : null)
                .policyName(alertRule.getPolicy() != null ? alertRule.getPolicy().getName() : null)
                .thresholdPercentage(alertRule.getThresholdPercentage())
                .windowSeconds(alertRule.getWindowSeconds())
                .cooldownSeconds(alertRule.getCooldownSeconds())
                .enabled(alertRule.isEnabled())
                .lastTriggeredAt(alertRule.getLastTriggeredAt())
                .createdAt(alertRule.getCreatedAt())
                .updatedAt(alertRule.getUpdatedAt())
                .build();
    }
}
