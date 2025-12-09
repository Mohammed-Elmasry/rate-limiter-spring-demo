package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.RateLimitCheckRequest;
import com.example.ratelimiter.application.dto.RateLimitCheckResponse;
import com.example.ratelimiter.domain.algorithm.AlgorithmFactory;
import com.example.ratelimiter.domain.algorithm.RateLimitAlgorithm;
import com.example.ratelimiter.domain.algorithm.RateLimitResult;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.RateLimitEvent;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.IdentifierType;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.infrastructure.resilience.FallbackHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final AlgorithmFactory algorithmFactory;
    private final PolicyRepository policyRepository;
    private final IpRuleService ipRuleService;
    private final PolicyRuleService policyRuleService;
    private final MetricsService metricsService;
    private final FallbackHandler fallbackHandler;

    @CircuitBreaker(name = "redis", fallbackMethod = "checkRateLimitFallback")
    @Retry(name = "redis")
    public RateLimitCheckResponse checkRateLimit(RateLimitCheckRequest request) {
        try {
            // Resolve the applicable policy
            Policy policy = resolvePolicy(request);

            if (policy == null) {
                log.warn("No policy found for request: {}", request);
                return handleNoPolicyFound();
            }

            if (!policy.isEnabled()) {
                log.warn("Policy {} is disabled, denying request", policy.getId());
                return RateLimitCheckResponse.denied(0, policy.getMaxRequests(), 0, policy.getId(), policy.getAlgorithm().name());
            }

            // Execute rate limit check using the Strategy pattern
            RateLimitAlgorithm algorithm = algorithmFactory.getAlgorithm(policy.getAlgorithm());
            RateLimitResult result = algorithm.execute(request.identifier(), policy);

            // Record metrics asynchronously (non-blocking)
            recordMetricsAsync(request, policy, result);

            // Build response
            if (result.allowed()) {
                return RateLimitCheckResponse.allowed(
                    result.remaining(),
                    policy.getMaxRequests(),
                    result.resetInSeconds(),
                    policy.getId(),
                    policy.getAlgorithm().name()
                );
            } else {
                return RateLimitCheckResponse.denied(
                    result.remaining(),
                    policy.getMaxRequests(),
                    result.resetInSeconds(),
                    policy.getId(),
                    policy.getAlgorithm().name()
                );
            }

        } catch (Exception e) {
            log.error("Error checking rate limit: {}", e.getMessage(), e);
            return handleError(request);
        }
    }

    private Policy resolvePolicy(RateLimitCheckRequest request) {
        // 1. Explicit Policy ID (highest priority)
        if (request.policyId() != null) {
            Optional<Policy> explicitPolicy = policyRepository.findById(request.policyId());
            if (explicitPolicy.isPresent()) {
                log.debug("Using explicit policy: {}", request.policyId());
                return explicitPolicy.get();
            }
        }

        // 2. Policy Rule Match (URL pattern and HTTP method based)
        if (request.resource() != null && !request.resource().isEmpty()) {
            Optional<Policy> rulePolicy = policyRuleService.findPolicyForResource(
                request.resource(),
                request.method()
            );
            if (rulePolicy.isPresent()) {
                log.debug("Using policy rule matched policy for resource: {} and method: {}",
                         request.resource(), request.method());
                return rulePolicy.get();
            }
        }

        // 3. IP-Specific Policy (if IP address is provided)
        if (request.ipAddress() != null && !request.ipAddress().isEmpty()) {
            Optional<Policy> ipPolicy = ipRuleService.getPolicyForIp(request.ipAddress(), request.tenantId());
            if (ipPolicy.isPresent()) {
                log.debug("Using IP-specific policy for IP: {}", request.ipAddress());
                return ipPolicy.get();
            }
        }

        // 4. Tenant Default Policy
        if (request.tenantId() != null) {
            Optional<Policy> tenantPolicy = policyRepository.findDefaultByTenantId(request.tenantId());
            if (tenantPolicy.isPresent()) {
                log.debug("Using tenant default policy for tenant: {}", request.tenantId());
                return tenantPolicy.get();
            }
        }

        // 5. Global Default Policy (fallback)
        Optional<Policy> globalPolicy = policyRepository.findDefaultByScope(PolicyScope.GLOBAL);
        if (globalPolicy.isPresent()) {
            log.debug("Using global default policy");
            return globalPolicy.get();
        }

        return null;
    }

    private RateLimitCheckResponse handleNoPolicyFound() {
        // Default behavior: fail closed (deny request)
        return RateLimitCheckResponse.denied(0, 0, 0, null, "NONE");
    }

    private RateLimitCheckResponse handleError(RateLimitCheckRequest request) {
        // Try to get a policy to determine fail mode
        Policy policy = resolvePolicy(request);

        if (policy != null && policy.getFailMode() == FailMode.FAIL_OPEN) {
            log.warn("Error occurred but policy fail mode is FAIL_OPEN, allowing request");
            return RateLimitCheckResponse.allowed(
                policy.getMaxRequests(),
                policy.getMaxRequests(),
                0,
                policy.getId(),
                policy.getAlgorithm().name()
            );
        }

        // Default: fail closed (deny request)
        log.warn("Error occurred and fail mode is FAIL_CLOSED, denying request");
        return RateLimitCheckResponse.denied(0, 0, 0, null, "ERROR");
    }

    /**
     * Records rate limit event metrics asynchronously.
     * This method creates a RateLimitEvent and delegates to MetricsService for async recording.
     * Any failures in metrics recording will not affect the rate limit check response.
     *
     * @param request The original rate limit check request
     * @param policy The policy that was applied
     * @param result The result of the rate limit check
     */
    private void recordMetricsAsync(RateLimitCheckRequest request, Policy policy, RateLimitResult result) {
        try {
            // Determine identifier type from the request scope
            IdentifierType identifierType = determineIdentifierType(request.scope());

            // Create the event
            RateLimitEvent event = RateLimitEvent.builder()
                .policyId(policy.getId())
                .identifier(request.identifier())
                .identifierType(identifierType)
                .allowed(result.allowed())
                .remaining((int) result.remaining())
                .limitValue(policy.getMaxRequests())
                .ipAddress(request.ipAddress())
                .resource(request.resource())
                .eventTime(OffsetDateTime.now())
                .build();

            // Record asynchronously (non-blocking)
            metricsService.recordEvent(event);

            // Also record gauge for real-time monitoring
            metricsService.recordUsageGauge(
                policy.getId(),
                request.identifier(),
                policy.getMaxRequests() - result.remaining(),
                policy.getMaxRequests()
            );

        } catch (Exception e) {
            // Log error but don't propagate - metrics recording should never fail the main flow
            log.error("Failed to record metrics for request: {}", e.getMessage(), e);
        }
    }

    /**
     * Determines the identifier type based on the scope string.
     * This is a simple heuristic mapping - you may want to make this more sophisticated
     * based on your business logic.
     *
     * @param scope The scope from the request
     * @return The corresponding IdentifierType
     */
    private IdentifierType determineIdentifierType(String scope) {
        if (scope == null) {
            return IdentifierType.CUSTOM;
        }

        return switch (scope.toUpperCase()) {
            case "API_KEY", "APIKEY" -> IdentifierType.API_KEY;
            case "IP", "IP_ADDRESS", "IPADDRESS" -> IdentifierType.IP;
            case "USER", "USER_ID", "USERID" -> IdentifierType.USER;
            case "SESSION", "SESSION_ID", "SESSIONID" -> IdentifierType.CUSTOM;
            case "TENANT" -> IdentifierType.TENANT;
            case "GLOBAL" -> IdentifierType.GLOBAL;
            default -> IdentifierType.CUSTOM;
        };
    }

    /**
     * Fallback method for circuit breaker.
     * Invoked when Redis is unavailable or circuit is open.
     *
     * @param request The rate limit request
     * @param throwable The exception that triggered the fallback
     * @return A response based on the policy's fail mode
     */
    private RateLimitCheckResponse checkRateLimitFallback(RateLimitCheckRequest request, Throwable throwable) {
        log.error("Circuit breaker triggered for rate limit check: {}", throwable.getMessage());

        // Try to resolve policy to determine fail mode
        try {
            Policy policy = resolvePolicyWithoutCircuitBreaker(request);
            if (policy != null) {
                return fallbackHandler.handleRateLimitCheckFailureWithPolicy(request, policy, throwable);
            }
        } catch (Exception e) {
            log.error("Failed to resolve policy in fallback: {}", e.getMessage());
        }

        // Default fallback behavior
        return fallbackHandler.handleRateLimitCheckFailure(request, throwable);
    }

    /**
     * Resolves policy without circuit breaker protection.
     * Used in fallback scenarios to avoid circular circuit breaker calls.
     *
     * @param request The rate limit request
     * @return The resolved policy or null
     */
    private Policy resolvePolicyWithoutCircuitBreaker(RateLimitCheckRequest request) {
        // 1. Explicit Policy ID (highest priority)
        if (request.policyId() != null) {
            Optional<Policy> explicitPolicy = policyRepository.findById(request.policyId());
            if (explicitPolicy.isPresent()) {
                return explicitPolicy.get();
            }
        }

        // 2. Policy Rule Match (URL pattern and HTTP method based)
        if (request.resource() != null && !request.resource().isEmpty()) {
            Optional<Policy> rulePolicy = policyRuleService.findPolicyForResource(
                request.resource(),
                request.method()
            );
            if (rulePolicy.isPresent()) {
                return rulePolicy.get();
            }
        }

        // 3. IP-Specific Policy (if IP address is provided)
        if (request.ipAddress() != null && !request.ipAddress().isEmpty()) {
            Optional<Policy> ipPolicy = ipRuleService.getPolicyForIp(request.ipAddress(), request.tenantId());
            if (ipPolicy.isPresent()) {
                return ipPolicy.get();
            }
        }

        // 4. Tenant Default Policy
        if (request.tenantId() != null) {
            Optional<Policy> tenantPolicy = policyRepository.findDefaultByTenantId(request.tenantId());
            if (tenantPolicy.isPresent()) {
                return tenantPolicy.get();
            }
        }

        // 5. Global Default Policy (fallback)
        Optional<Policy> globalPolicy = policyRepository.findDefaultByScope(PolicyScope.GLOBAL);
        return globalPolicy.orElse(null);
    }
}
