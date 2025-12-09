package com.example.ratelimiter.application.mapper;

import com.example.ratelimiter.application.dto.PolicyRuleRequest;
import com.example.ratelimiter.application.dto.PolicyRuleResponse;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.PolicyRule;
import org.springframework.stereotype.Component;

/**
 * Mapper for PolicyRule entity and DTOs.
 * Provides conversion methods between entity and request/response objects.
 */
@Component
public class PolicyRuleMapper {

    /**
     * Converts a PolicyRuleRequest to a PolicyRule entity.
     * Note: The policy must be set separately after creation.
     *
     * @param request The request DTO
     * @param policy The policy to associate with this rule
     * @return The PolicyRule entity
     */
    public PolicyRule toEntity(PolicyRuleRequest request, Policy policy) {
        return PolicyRule.builder()
                .policy(policy)
                .name(request.name())
                .resourcePattern(request.resourcePattern())
                .httpMethods(request.httpMethods())
                .priority(request.priority())
                .enabled(request.enabled())
                .build();
    }

    /**
     * Updates an existing PolicyRule entity with values from the request.
     *
     * @param policyRule The existing entity to update
     * @param request The request DTO with new values
     * @param policy The policy to associate with this rule
     */
    public void updateEntity(PolicyRule policyRule, PolicyRuleRequest request, Policy policy) {
        policyRule.setPolicy(policy);
        policyRule.setName(request.name());
        policyRule.setResourcePattern(request.resourcePattern());
        policyRule.setHttpMethods(request.httpMethods());
        policyRule.setPriority(request.priority());
        policyRule.setEnabled(request.enabled());
    }

    /**
     * Converts a PolicyRule entity to a PolicyRuleResponse DTO.
     *
     * @param policyRule The entity
     * @return The response DTO
     */
    public PolicyRuleResponse toResponse(PolicyRule policyRule) {
        return PolicyRuleResponse.from(policyRule);
    }
}
