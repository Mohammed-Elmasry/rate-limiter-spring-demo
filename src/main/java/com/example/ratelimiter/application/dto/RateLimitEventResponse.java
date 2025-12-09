package com.example.ratelimiter.application.dto;

import com.example.ratelimiter.domain.entity.RateLimitEvent;
import com.example.ratelimiter.domain.enums.IdentifierType;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record RateLimitEventResponse(
        Long id,
        UUID policyId,
        String identifier,
        IdentifierType identifierType,
        boolean allowed,
        Integer remaining,
        Integer limitValue,
        String ipAddress,
        String resource,
        OffsetDateTime eventTime,
        String partitionKey
) {
    public static RateLimitEventResponse from(RateLimitEvent event) {
        return RateLimitEventResponse.builder()
                .id(event.getId())
                .policyId(event.getPolicyId())
                .identifier(event.getIdentifier())
                .identifierType(event.getIdentifierType())
                .allowed(event.isAllowed())
                .remaining(event.getRemaining())
                .limitValue(event.getLimitValue())
                .ipAddress(event.getIpAddress())
                .resource(event.getResource())
                .eventTime(event.getEventTime())
                .partitionKey(event.getPartitionKey())
                .build();
    }
}
