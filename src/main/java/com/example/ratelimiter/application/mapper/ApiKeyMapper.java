package com.example.ratelimiter.application.mapper;

import com.example.ratelimiter.application.dto.ApiKeyRequest;
import com.example.ratelimiter.domain.entity.ApiKey;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyMapper {

    public ApiKey toEntity(ApiKeyRequest request, String keyHash, String keyPrefix, Tenant tenant, Policy policy) {
        return ApiKey.builder()
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .name(request.name())
                .tenant(tenant)
                .policy(policy)
                .enabled(request.enabled())
                .expiresAt(request.expiresAt())
                .build();
    }

    public void updateEntity(ApiKey apiKey, ApiKeyRequest request, Policy policy) {
        apiKey.setName(request.name());
        apiKey.setPolicy(policy);
        apiKey.setEnabled(request.enabled());
        apiKey.setExpiresAt(request.expiresAt());
    }
}
