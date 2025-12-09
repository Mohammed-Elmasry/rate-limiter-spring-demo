package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.IpRuleRequest;
import com.example.ratelimiter.application.dto.IpRuleResponse;
import com.example.ratelimiter.application.service.IpRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ip-rules")
@RequiredArgsConstructor
public class IpRuleController {

    private final IpRuleService ipRuleService;

    @GetMapping
    public ResponseEntity<List<IpRuleResponse>> findAll() {
        return ResponseEntity.ok(ipRuleService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IpRuleResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ipRuleService.findById(id));
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<IpRuleResponse>> findByTenantId(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ipRuleService.findByTenantId(tenantId));
    }

    @GetMapping("/rule-type/{ruleType}")
    public ResponseEntity<List<IpRuleResponse>> findByRuleType(@PathVariable String ruleType) {
        return ResponseEntity.ok(ipRuleService.findByRuleType(ruleType));
    }

    @GetMapping("/match/{ip}")
    public ResponseEntity<IpRuleResponse> findMatchingRule(@PathVariable String ip) {
        return ipRuleService.findMatchingRateLimitRuleForIp(ip)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/match/{ip}/tenant/{tenantId}")
    public ResponseEntity<IpRuleResponse> findMatchingRuleForTenant(
            @PathVariable String ip,
            @PathVariable UUID tenantId) {
        return ipRuleService.findMatchingRateLimitRuleForIpAndTenant(ip, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<IpRuleResponse> create(@Valid @RequestBody IpRuleRequest request) {
        IpRuleResponse response = ipRuleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IpRuleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody IpRuleRequest request) {
        return ResponseEntity.ok(ipRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ipRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
