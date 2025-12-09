package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.PolicyRequest;
import com.example.ratelimiter.application.dto.PolicyResponse;
import com.example.ratelimiter.application.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping
    public ResponseEntity<List<PolicyResponse>> findAll() {
        return ResponseEntity.ok(policyService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(policyService.findById(id));
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<PolicyResponse>> findByTenantId(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(policyService.findByTenantId(tenantId));
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody PolicyRequest request) {
        PolicyResponse response = policyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PolicyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PolicyRequest request) {
        return ResponseEntity.ok(policyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        policyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
