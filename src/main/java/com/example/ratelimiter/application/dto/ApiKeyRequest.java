package com.example.ratelimiter.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ApiKeyRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @NotNull(message = "Tenant ID is required")
        UUID tenantId,

        UUID policyId,

        Boolean enabled,

        OffsetDateTime expiresAt
) {
    public ApiKeyRequest {
        if (enabled == null) {
            enabled = true;
        }
    }
}
