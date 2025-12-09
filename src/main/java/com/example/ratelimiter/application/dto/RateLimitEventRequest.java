package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.enums.IdentifierType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record RateLimitEventRequest(
        @NotNull(message = "Policy ID is required")
        UUID policyId,

        @NotBlank(message = "Identifier is required")
        @Size(max = 255, message = "Identifier must not exceed 255 characters")
        String identifier,

        @NotNull(message = "Identifier type is required")
        IdentifierType identifierType,

        @NotNull(message = "Allowed flag is required")
        Boolean allowed,

        @NotNull(message = "Remaining count is required")
        @Min(value = 0, message = "Remaining count must be non-negative")
        Integer remaining,

        @NotNull(message = "Limit value is required")
        @Min(value = 1, message = "Limit value must be at least 1")
        Integer limitValue,

        String ipAddress,

        @Size(max = 255, message = "Resource must not exceed 255 characters")
        String resource,

        OffsetDateTime eventTime
) {
    public RateLimitEventRequest {
        if (eventTime == null) {
            eventTime = OffsetDateTime.now();
        }
    }
}
