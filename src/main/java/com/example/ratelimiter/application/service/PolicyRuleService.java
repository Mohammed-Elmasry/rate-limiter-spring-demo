package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.PolicyRuleRequest;
import com.example.ratelimiter.application.dto.PolicyRuleResponse;
import com.example.ratelimiter.application.mapper.PolicyRuleMapper;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.PolicyRule;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.PolicyRuleRepository;
import com.example.ratelimiter.infrastructure.resilience.FallbackHandler;
import com.example.ratelimiter.infrastructure.util.PathPatternMatcher;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PolicyRuleService {

    private final PolicyRuleRepository policyRuleRepository;
    private final PolicyRepository policyRepository;
    private final PolicyRuleMapper policyRuleMapper;
    private final PathPatternMatcher pathPatternMatcher;
    private final FallbackHandler fallbackHandler;

    @CircuitBreaker(name = "database", fallbackMethod = "findAllFallback")
    @Retry(name = "database")
    public List<PolicyRuleResponse> findAll() {
        return policyRuleRepository.findAll().stream()
                .map(policyRuleMapper::toResponse)
                .toList();
    }

    @CircuitBreaker(name = "database", fallbackMethod = "findByIdFallback")
    @Retry(name = "database")
    public PolicyRuleResponse findById(UUID id) {
        PolicyRule policyRule = policyRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Policy rule not found with id: " + id));
        return policyRuleMapper.toResponse(policyRule);
    }

    @CircuitBreaker(name = "database", fallbackMethod = "findByPolicyIdFallback")
    @Retry(name = "database")
    public List<PolicyRuleResponse> findByPolicyId(UUID policyId) {
        return policyRuleRepository.findByPolicyId(policyId).stream()
                .map(policyRuleMapper::toResponse)
                .toList();
    }

    /**
     * Finds the first matching policy rule for the given resource path and HTTP method.
     * Rules are evaluated in order of priority (highest first).
     * Returns the first rule that matches both the path pattern and HTTP method.
     *
     * @param resourcePath The resource path to match (e.g., /api/v1/users/123)
     * @param httpMethod The HTTP method (e.g., GET, POST) or null to ignore method matching
     * @return The matching policy rule response, or empty if no match found
     */
    @CircuitBreaker(name = "database", fallbackMethod = "findMatchingRuleFallback")
    @Retry(name = "database")
    public Optional<PolicyRuleResponse> findMatchingRule(String resourcePath, String httpMethod) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            log.warn("Resource path is null or empty");
            return Optional.empty();
        }

        // Get all enabled rules ordered by priority
        List<PolicyRule> enabledRules = policyRuleRepository.findAllEnabledOrderedByPriority();

        log.debug("Checking {} enabled rules for path: {} and method: {}",
                  enabledRules.size(), resourcePath, httpMethod);

        // Find first matching rule
        for (PolicyRule rule : enabledRules) {
            // Check path pattern match
            if (pathPatternMatcher.matches(rule.getResourcePattern(), resourcePath)) {
                // Check HTTP method match
                if (rule.matchesHttpMethod(httpMethod)) {
                    log.info("Found matching rule: {} (priority: {}) for path: {} and method: {}",
                             rule.getName(), rule.getPriority(), resourcePath, httpMethod);
                    return Optional.of(policyRuleMapper.toResponse(rule));
                } else {
                    log.debug("Rule {} matches path but not HTTP method: {} vs {}",
                              rule.getName(), rule.getHttpMethods(), httpMethod);
                }
            }
        }

        log.debug("No matching rule found for path: {} and method: {}", resourcePath, httpMethod);
        return Optional.empty();
    }

    /**
     * Finds the policy associated with the first matching rule.
     * This is a convenience method for rate limiting integration.
     *
     * @param resourcePath The resource path to match
     * @param httpMethod The HTTP method or null
     * @return The policy if a matching rule is found, empty otherwise
     */
    @CircuitBreaker(name = "database", fallbackMethod = "findPolicyForResourceFallback")
    @Retry(name = "database")
    public Optional<Policy> findPolicyForResource(String resourcePath, String httpMethod) {
        Optional<PolicyRuleResponse> matchingRule = findMatchingRule(resourcePath, httpMethod);

        if (matchingRule.isPresent()) {
            UUID policyId = matchingRule.get().policyId();
            return policyRepository.findById(policyId);
        }

        return Optional.empty();
    }

    @Transactional
    public PolicyRuleResponse create(PolicyRuleRequest request) {
        // Validate policy exists
        Policy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));

        // Validate pattern
        if (!pathPatternMatcher.isValidPattern(request.resourcePattern())) {
            throw new IllegalArgumentException("Invalid resource pattern: " + request.resourcePattern());
        }

        // Check for duplicate name within the same policy
        if (policyRuleRepository.existsByNameAndPolicyId(request.name(), request.policyId())) {
            throw new IllegalArgumentException("A rule with name '" + request.name() +
                                             "' already exists for this policy");
        }

        PolicyRule policyRule = policyRuleMapper.toEntity(request, policy);
        PolicyRule saved = policyRuleRepository.save(policyRule);

        log.info("Created policy rule: {} (id: {}) for policy: {}",
                 saved.getName(), saved.getId(), policy.getName());

        return policyRuleMapper.toResponse(saved);
    }

    @Transactional
    public PolicyRuleResponse update(UUID id, PolicyRuleRequest request) {
        PolicyRule policyRule = policyRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Policy rule not found with id: " + id));

        // Validate policy exists
        Policy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));

        // Validate pattern
        if (!pathPatternMatcher.isValidPattern(request.resourcePattern())) {
            throw new IllegalArgumentException("Invalid resource pattern: " + request.resourcePattern());
        }

        // Check for duplicate name (excluding current rule)
        PolicyRule existingWithName = policyRuleRepository.findAll().stream()
                .filter(r -> r.getName().equals(request.name()) &&
                           r.getPolicy().getId().equals(request.policyId()) &&
                           !r.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (existingWithName != null) {
            throw new IllegalArgumentException("A rule with name '" + request.name() +
                                             "' already exists for this policy");
        }

        policyRuleMapper.updateEntity(policyRule, request, policy);
        PolicyRule saved = policyRuleRepository.save(policyRule);

        log.info("Updated policy rule: {} (id: {})", saved.getName(), saved.getId());

        return policyRuleMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!policyRuleRepository.existsById(id)) {
            throw new EntityNotFoundException("Policy rule not found with id: " + id);
        }

        policyRuleRepository.deleteById(id);
        log.info("Deleted policy rule with id: {}", id);
    }

    // ======================== Circuit Breaker Fallback Methods ========================

    private List<PolicyRuleResponse> findAllFallback(Throwable throwable) {
        log.error("Database circuit breaker triggered in findAll: {}", throwable.getMessage());
        return java.util.Collections.emptyList();
    }

    private PolicyRuleResponse findByIdFallback(UUID id, Throwable throwable) {
        log.error("Database circuit breaker triggered in findById for id {}: {}",
                  id, throwable.getMessage());
        return null;
    }

    private List<PolicyRuleResponse> findByPolicyIdFallback(UUID policyId, Throwable throwable) {
        log.error("Database circuit breaker triggered in findByPolicyId for policy {}: {}",
                  policyId, throwable.getMessage());
        return java.util.Collections.emptyList();
    }

    private Optional<PolicyRuleResponse> findMatchingRuleFallback(String resourcePath, String httpMethod,
                                                                   Throwable throwable) {
        log.error("Database circuit breaker triggered in findMatchingRule for path {} and method {}: {}",
                  resourcePath, httpMethod, throwable.getMessage());
        return Optional.empty();
    }

    private Optional<Policy> findPolicyForResourceFallback(String resourcePath, String httpMethod,
                                                           Throwable throwable) {
        log.error("Database circuit breaker triggered in findPolicyForResource for path {} and method {}: {}",
                  resourcePath, httpMethod, throwable.getMessage());
        return Optional.empty();
    }
}
