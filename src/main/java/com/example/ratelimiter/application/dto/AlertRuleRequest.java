package com.example.ratelimiter.application.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.util.UUID;

@Builder
public record AlertRuleRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        UUID policyId,

        @NotNull(message = "Threshold percentage is required")
        @Min(value = 1, message = "Threshold percentage must be at least 1")
        @Max(value = 100, message = "Threshold percentage must not exceed 100")
        Integer thresholdPercentage,

        @Min(value = 1, message = "Window seconds must be at least 1")
        Integer windowSeconds,

        @Min(value = 0, message = "Cooldown seconds must be non-negative")
        Integer cooldownSeconds,

        Boolean enabled
) {
    public AlertRuleRequest {
        if (windowSeconds == null) {
            windowSeconds = 60;
        }
        if (cooldownSeconds == null) {
            cooldownSeconds = 300;
        }
        if (enabled == null) {
            enabled = true;
        }
    }
}
