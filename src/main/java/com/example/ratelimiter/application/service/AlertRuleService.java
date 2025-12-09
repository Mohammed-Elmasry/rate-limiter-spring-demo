package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.AlertRuleRequest;
import com.example.ratelimiter.application.dto.AlertRuleResponse;
import com.example.ratelimiter.domain.entity.AlertRule;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.repository.AlertRuleRepository;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final PolicyRepository policyRepository;

    public List<AlertRuleResponse> findAll() {
        return alertRuleRepository.findAll().stream()
                .map(AlertRuleResponse::from)
                .toList();
    }

    public AlertRuleResponse findById(UUID id) {
        return alertRuleRepository.findById(id)
                .map(AlertRuleResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found with id: " + id));
    }

    @Transactional
    public AlertRuleResponse create(AlertRuleRequest request) {
        Policy policy = null;
        if (request.policyId() != null) {
            policy = policyRepository.findById(request.policyId())
                    .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));
        }

        AlertRule alertRule = AlertRule.builder()
                .name(request.name())
                .policy(policy)
                .thresholdPercentage(request.thresholdPercentage())
                .windowSeconds(request.windowSeconds())
                .cooldownSeconds(request.cooldownSeconds())
                .enabled(request.enabled())
                .build();

        AlertRule saved = alertRuleRepository.save(alertRule);
        return AlertRuleResponse.from(saved);
    }

    @Transactional
    public AlertRuleResponse update(UUID id, AlertRuleRequest request) {
        AlertRule alertRule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found with id: " + id));

        Policy policy = null;
        if (request.policyId() != null) {
            policy = policyRepository.findById(request.policyId())
                    .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));
        }

        alertRule.setName(request.name());
        alertRule.setPolicy(policy);
        alertRule.setThresholdPercentage(request.thresholdPercentage());
        alertRule.setWindowSeconds(request.windowSeconds());
        alertRule.setCooldownSeconds(request.cooldownSeconds());
        alertRule.setEnabled(request.enabled());

        AlertRule saved = alertRuleRepository.save(alertRule);
        return AlertRuleResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!alertRuleRepository.existsById(id)) {
            throw new EntityNotFoundException("Alert rule not found with id: " + id);
        }
        alertRuleRepository.deleteById(id);
    }
}
