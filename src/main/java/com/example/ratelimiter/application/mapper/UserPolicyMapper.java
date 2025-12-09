package com.example.ratelimiter.application.mapper;

import com.example.ratelimiter.application.dto.UserPolicyRequest;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.entity.UserPolicy;
import org.springframework.stereotype.Component;

@Component
public class UserPolicyMapper {

    public UserPolicy toEntity(UserPolicyRequest request, Policy policy, Tenant tenant) {
        return UserPolicy.builder()
                .userId(request.userId())
                .policy(policy)
                .tenant(tenant)
                .enabled(request.enabled())
                .build();
    }

    public void updateEntity(UserPolicy userPolicy, UserPolicyRequest request, Policy policy) {
        userPolicy.setUserId(request.userId());
        userPolicy.setPolicy(policy);
        userPolicy.setEnabled(request.enabled());
    }
}
