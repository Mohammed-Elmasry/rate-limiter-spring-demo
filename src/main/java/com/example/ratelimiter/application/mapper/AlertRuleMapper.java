package com.example.ratelimiter.application.mapper;

import com.example.ratelimiter.application.dto.AlertRuleRequest;
import com.example.ratelimiter.domain.entity.AlertRule;
import com.example.ratelimiter.domain.entity.Policy;
import org.springframework.stereotype.Component;

@Component
public class AlertRuleMapper {

    public AlertRule toEntity(AlertRuleRequest request, Policy policy) {
        return AlertRule.builder()
                .name(request.name())
                .policy(policy)
                .thresholdPercentage(request.thresholdPercentage())
                .windowSeconds(request.windowSeconds())
                .cooldownSeconds(request.cooldownSeconds())
                .enabled(request.enabled())
                .build();
    }

    public void updateEntity(AlertRule alertRule, AlertRuleRequest request, Policy policy) {
        alertRule.setName(request.name());
        alertRule.setPolicy(policy);
        alertRule.setThresholdPercentage(request.thresholdPercentage());
        alertRule.setWindowSeconds(request.windowSeconds());
        alertRule.setCooldownSeconds(request.cooldownSeconds());
        alertRule.setEnabled(request.enabled());
    }
}
