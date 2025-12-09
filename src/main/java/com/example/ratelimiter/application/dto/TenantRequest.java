package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.enums.TenantTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record TenantRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @NotNull(message = "Tier is required")
        TenantTier tier,

        Boolean enabled
) {
    public TenantRequest {
        if (enabled == null) {
            enabled = true;
        }
    }
}
