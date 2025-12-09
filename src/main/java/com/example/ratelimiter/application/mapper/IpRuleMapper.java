package com.example.ratelimiter.application.mapper;

import com.example.ratelimiter.application.dto.IpRuleRequest;
import com.example.ratelimiter.domain.entity.IpRule;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import org.springframework.stereotype.Component;

@Component
public class IpRuleMapper {

    public IpRule toEntity(IpRuleRequest request, Policy policy, Tenant tenant) {
        return IpRule.builder()
                .ipAddress(request.ipAddress())
                .ipCidr(request.ipCidr())
                .ruleType(request.ruleType())
                .policy(policy)
                .tenant(tenant)
                .description(request.description())
                .enabled(request.enabled())
                .build();
    }

    public void updateEntity(IpRule ipRule, IpRuleRequest request, Policy policy, Tenant tenant) {
        ipRule.setIpAddress(request.ipAddress());
        ipRule.setIpCidr(request.ipCidr());
        ipRule.setRuleType(request.ruleType());
        ipRule.setPolicy(policy);
        ipRule.setTenant(tenant);
        ipRule.setDescription(request.description());
        ipRule.setEnabled(request.enabled());
    }
}
