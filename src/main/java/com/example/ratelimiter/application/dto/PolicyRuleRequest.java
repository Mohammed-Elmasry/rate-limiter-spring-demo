package com.example.ratelimiter.application.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.util.UUID;

@Builder
public record PolicyRuleRequest(
        @NotNull(message = "Policy ID is required")
        UUID policyId,

        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Resource pattern is required")
        @Size(max = 500, message = "Resource pattern must not exceed 500 characters")
        @Pattern(regexp = "^/.*", message = "Resource pattern must start with /")
        String resourcePattern,

        @Size(max = 100, message = "HTTP methods must not exceed 100 characters")
        @Pattern(regexp = "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)(,(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS))*$",
                 message = "HTTP methods must be comma-separated valid HTTP methods (GET,POST,PUT,DELETE,PATCH,HEAD,OPTIONS)",
                 flags = Pattern.Flag.CASE_INSENSITIVE)
        String httpMethods,

        @NotNull(message = "Priority is required")
        @Min(value = 0, message = "Priority must be at least 0")
        @Max(value = 1000, message = "Priority must not exceed 1000")
        Integer priority,

        Boolean enabled
) {
    public PolicyRuleRequest {
        if (enabled == null) {
            enabled = true;
        }
        if (priority == null) {
            priority = 0;
        }
    }
}
