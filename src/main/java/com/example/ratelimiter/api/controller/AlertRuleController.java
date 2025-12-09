package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.AlertRuleRequest;
import com.example.ratelimiter.application.dto.AlertRuleResponse;
import com.example.ratelimiter.application.service.AlertRuleService;
import com.example.ratelimiter.application.service.AlertingService;
import com.example.ratelimiter.domain.entity.AlertRule;
import com.example.ratelimiter.domain.repository.AlertRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;
    private final AlertingService alertingService;
    private final AlertRuleRepository alertRuleRepository;

    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> findAll() {
        return ResponseEntity.ok(alertRuleService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(alertRuleService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AlertRuleResponse> create(@Valid @RequestBody AlertRuleRequest request) {
        AlertRuleResponse response = alertRuleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AlertRuleRequest request) {
        return ResponseEntity.ok(alertRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        alertRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Trigger a test notification for an alert rule.
     *
     * This endpoint allows testing the notification configuration without
     * waiting for actual threshold violations. It forces an alert to be
     * triggered with current metrics.
     *
     * @param id The alert rule ID
     * @return Success message
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, String>> testAlert(@PathVariable UUID id) {
        AlertRule alertRule = alertRuleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Alert rule not found with id: " + id));

        if (!alertRule.isEnabled()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Cannot test disabled alert rule"));
        }

        if (alertRule.getPolicy() == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Alert rule has no associated policy"));
        }

        // Force trigger the alert regardless of threshold or cooldown
        alertingService.checkAndTriggerAlert(alertRule);

        return ResponseEntity.ok(Map.of(
            "message", "Test alert triggered successfully",
            "alertRuleId", id.toString(),
            "alertRuleName", alertRule.getName()
        ));
    }
}
