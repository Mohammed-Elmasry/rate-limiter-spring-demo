package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.entity.IpRule;
import com.example.ratelimiter.domain.enums.RuleType;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record IpRuleResponse(
        UUID id,
        String ipAddress,
        String ipCidr,
        RuleType ruleType,
        UUID policyId,
        String policyName,
        UUID tenantId,
        String description,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static IpRuleResponse from(IpRule ipRule) {
        return IpRuleResponse.builder()
                .id(ipRule.getId())
                .ipAddress(ipRule.getIpAddress())
                .ipCidr(ipRule.getIpCidr())
                .ruleType(ipRule.getRuleType())
                .policyId(ipRule.getPolicy() != null ? ipRule.getPolicy().getId() : null)
                .policyName(ipRule.getPolicy() != null ? ipRule.getPolicy().getName() : null)
                .tenantId(ipRule.getTenant() != null ? ipRule.getTenant().getId() : null)
                .description(ipRule.getDescription())
                .enabled(ipRule.isEnabled())
                .createdAt(ipRule.getCreatedAt())
                .updatedAt(ipRule.getUpdatedAt())
                .build();
    }
}
