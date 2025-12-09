package com.example.ratelimiter.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UserPolicyRequest(
        @NotBlank(message = "User ID is required")
        @Size(max = 255, message = "User ID must not exceed 255 characters")
        String userId,

        @NotNull(message = "Policy ID is required")
        UUID policyId,

        @NotNull(message = "Tenant ID is required")
        UUID tenantId,

        Boolean enabled
) {
    public UserPolicyRequest {
        if (enabled == null) {
            enabled = true;
        }
    }
}
