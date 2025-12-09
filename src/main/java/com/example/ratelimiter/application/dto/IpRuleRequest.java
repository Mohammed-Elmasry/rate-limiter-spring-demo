package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.enums.RuleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
public record IpRuleRequest(
        @Pattern(
            regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$",
            message = "Invalid IP address format"
        )
        String ipAddress,

        @Pattern(
            regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/([0-9]|[1-2][0-9]|3[0-2])$",
            message = "Invalid CIDR notation format"
        )
        String ipCidr,

        @NotNull(message = "Rule type is required")
        RuleType ruleType,

        @NotNull(message = "Policy ID is required for RATE_LIMIT rule type")
        UUID policyId,

        UUID tenantId,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        Boolean enabled
) {
    public IpRuleRequest {
        if (ipAddress == null && ipCidr == null) {
            throw new IllegalArgumentException("Either ipAddress or ipCidr must be provided");
        }
        if (ipAddress != null && ipCidr != null) {
            throw new IllegalArgumentException("Only one of ipAddress or ipCidr can be provided");
        }
        if (enabled == null) {
            enabled = true;
        }
        if (ruleType == null) {
            ruleType = RuleType.RATE_LIMIT;
        }
        if (!RuleType.RATE_LIMIT.equals(ruleType)) {
            throw new IllegalArgumentException("Only RATE_LIMIT rule type is currently supported");
        }
        if (policyId == null) {
            throw new IllegalArgumentException("Policy ID is required for RATE_LIMIT rule type");
        }
    }
}
