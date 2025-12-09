package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PolicyRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @NotNull(message = "Scope is required")
        PolicyScope scope,

        @NotNull(message = "Algorithm is required")
        Algorithm algorithm,

        @NotNull(message = "Max requests is required")
        @Min(value = 1, message = "Max requests must be at least 1")
        Integer maxRequests,

        @NotNull(message = "Window seconds is required")
        @Min(value = 1, message = "Window seconds must be at least 1")
        Integer windowSeconds,

        @Min(value = 1, message = "Burst capacity must be at least 1")
        Integer burstCapacity,

        BigDecimal refillRate,

        FailMode failMode,

        Boolean enabled,

        Boolean isDefault,

        UUID tenantId
) {
    public PolicyRequest {
        if (failMode == null) {
            failMode = FailMode.FAIL_CLOSED;
        }
        if (enabled == null) {
            enabled = true;
        }
        if (isDefault == null) {
            isDefault = false;
        }
    }
}
