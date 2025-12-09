package com.example.ratelimiter.application.mapper;

import com.example.ratelimiter.application.dto.RateLimitEventRequest;
import com.example.ratelimiter.domain.entity.RateLimitEvent;
import org.springframework.stereotype.Component;

@Component
public class RateLimitEventMapper {

    public RateLimitEvent toEntity(RateLimitEventRequest request) {
        return RateLimitEvent.builder()
                .policyId(request.policyId())
                .identifier(request.identifier())
                .identifierType(request.identifierType())
                .allowed(request.allowed())
                .remaining(request.remaining())
                .limitValue(request.limitValue())
                .ipAddress(request.ipAddress())
                .resource(request.resource())
                .eventTime(request.eventTime())
                .build();
    }
}
