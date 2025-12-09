package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.PolicyRuleRequest;
import com.example.ratelimiter.application.dto.PolicyRuleResponse;
import com.example.ratelimiter.application.service.PolicyRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/policy-rules")
@RequiredArgsConstructor
public class PolicyRuleController {

    private final PolicyRuleService policyRuleService;

    /**
     * Get all policy rules.
     *
     * @return List of all policy rules
     */
    @GetMapping
    public ResponseEntity<List<PolicyRuleResponse>> findAll() {
        return ResponseEntity.ok(policyRuleService.findAll());
    }

    /**
     * Get a policy rule by ID.
     *
     * @param id The policy rule ID
     * @return The policy rule
     */
    @GetMapping("/{id}")
    public ResponseEntity<PolicyRuleResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(policyRuleService.findById(id));
    }

    /**
     * Get all policy rules for a specific policy.
     *
     * @param policyId The policy ID
     * @return List of policy rules for the policy
     */
    @GetMapping("/policy/{policyId}")
    public ResponseEntity<List<PolicyRuleResponse>> findByPolicyId(@PathVariable UUID policyId) {
        return ResponseEntity.ok(policyRuleService.findByPolicyId(policyId));
    }

    /**
     * Find the first matching policy rule for a given resource path and HTTP method.
     * This endpoint is useful for testing rule matching logic.
     *
     * @param path The resource path to match (e.g., /api/v1/users/123)
     * @param method The HTTP method (e.g., GET, POST). Optional - if not provided, method matching is ignored.
     * @return The matching policy rule or 404 if no match found
     */
    @GetMapping("/match")
    public ResponseEntity<PolicyRuleResponse> findMatchingRule(
            @RequestParam String path,
            @RequestParam(required = false) String method) {

        Optional<PolicyRuleResponse> matchingRule = policyRuleService.findMatchingRule(path, method);

        return matchingRule
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create a new policy rule.
     *
     * @param request The policy rule request
     * @return The created policy rule
     */
    @PostMapping
    public ResponseEntity<PolicyRuleResponse> create(@Valid @RequestBody PolicyRuleRequest request) {
        PolicyRuleResponse response = policyRuleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing policy rule.
     *
     * @param id The policy rule ID
     * @param request The updated policy rule data
     * @return The updated policy rule
     */
    @PutMapping("/{id}")
    public ResponseEntity<PolicyRuleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PolicyRuleRequest request) {
        return ResponseEntity.ok(policyRuleService.update(id, request));
    }

    /**
     * Delete a policy rule.
     *
     * @param id The policy rule ID
     * @return No content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        policyRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
