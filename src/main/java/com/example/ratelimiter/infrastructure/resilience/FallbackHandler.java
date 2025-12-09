package com.example.ratelimiter.infrastructure.resilience;

import com.example.ratelimiter.application.dto.PolicyResponse;
import com.example.ratelimiter.application.dto.RateLimitCheckRequest;
import com.example.ratelimiter.application.dto.RateLimitCheckResponse;
import com.example.ratelimiter.domain.algorithm.RateLimitResult;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.FailMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fallback handler for circuit breaker failures.
 *
 * This class provides fallback behavior when services are unavailable:
 * - Redis failures: Return fail-open or fail-closed based on Policy configuration
 * - Database failures: Return cached data or safe defaults
 *
 * Design Principles:
 * 1. Graceful Degradation: System remains operational even when dependencies fail
 * 2. Fail-Safe Defaults: When in doubt, fail closed to protect backend services
 * 3. Policy-Aware: Respects Policy.failMode configuration
 * 4. Observable: Logs all fallback invocations for monitoring
 *
 * Fallback Strategies:
 * - FAIL_OPEN: Allow requests to pass through (use when Redis is down)
 * - FAIL_CLOSED: Deny all requests (default safe behavior)
 */
@Component
@Slf4j
public class FallbackHandler {

    /**
     * Fallback for Redis circuit breaker in rate limiting operations.
     *
     * When Redis is unavailable, we need to make a decision:
     * - If policy specifies FAIL_OPEN: Allow the request (prevent denial of service)
     * - If policy specifies FAIL_CLOSED: Deny the request (protect backend services)
     *
     * @param key The rate limit key
     * @param policy The applicable policy
     * @param throwable The exception that triggered the fallback
     * @return A RateLimitResult based on the policy's fail mode
     */
    public RateLimitResult handleRedisFailure(String key, Policy policy, Throwable throwable) {
        log.error("Redis circuit breaker triggered for key: {}, policy: {}, error: {}",
                key, policy != null ? policy.getId() : "null", throwable.getMessage());

        if (policy == null) {
            log.warn("No policy available for fallback, defaulting to FAIL_CLOSED");
            return RateLimitResult.denied(0, 0, 0);
        }

        FailMode failMode = policy.getFailMode() != null ? policy.getFailMode() : FailMode.FAIL_CLOSED;

        if (failMode == FailMode.FAIL_OPEN) {
            log.warn("Redis unavailable but policy {} is FAIL_OPEN, allowing request", policy.getId());
            return RateLimitResult.allowed(
                    policy.getMaxRequests(),
                    0
            );
        } else {
            log.warn("Redis unavailable and policy {} is FAIL_CLOSED, denying request", policy.getId());
            return RateLimitResult.denied(0, 0, 0);
        }
    }

    /**
     * Fallback for rate limit check when the entire operation fails.
     *
     * @param request The rate limit request
     * @param throwable The exception that triggered the fallback
     * @return A safe default response
     */
    public RateLimitCheckResponse handleRateLimitCheckFailure(
            RateLimitCheckRequest request,
            Throwable throwable) {
        log.error("Rate limit check failed for request: {}, error: {}",
                request, throwable.getMessage());

        // Default to fail closed for safety
        return RateLimitCheckResponse.denied(
                0,
                0,
                0,
                null,
                "CIRCUIT_BREAKER_OPEN"
        );
    }

    /**
     * Fallback for rate limit check with policy context.
     *
     * @param request The rate limit request
     * @param policy The applicable policy
     * @param throwable The exception that triggered the fallback
     * @return A response based on the policy's fail mode
     */
    public RateLimitCheckResponse handleRateLimitCheckFailureWithPolicy(
            RateLimitCheckRequest request,
            Policy policy,
            Throwable throwable) {
        log.error("Rate limit check failed for request: {}, policy: {}, error: {}",
                request, policy != null ? policy.getId() : "null", throwable.getMessage());

        if (policy == null) {
            return handleRateLimitCheckFailure(request, throwable);
        }

        FailMode failMode = policy.getFailMode() != null ? policy.getFailMode() : FailMode.FAIL_CLOSED;

        if (failMode == FailMode.FAIL_OPEN) {
            log.warn("Circuit breaker open but policy is FAIL_OPEN, allowing request");
            return RateLimitCheckResponse.allowed(
                    policy.getMaxRequests(),
                    policy.getMaxRequests(),
                    0,
                    policy.getId(),
                    policy.getAlgorithm().name()
            );
        } else {
            log.warn("Circuit breaker open and policy is FAIL_CLOSED, denying request");
            return RateLimitCheckResponse.denied(
                    0,
                    policy.getMaxRequests(),
                    0,
                    policy.getId(),
                    policy.getAlgorithm().name()
            );
        }
    }

    /**
     * Fallback for database circuit breaker in policy retrieval.
     *
     * When database is unavailable, return an empty list to prevent cascading failures.
     * Calling code should have caching in place to minimize impact.
     *
     * @param throwable The exception that triggered the fallback
     * @return An empty list
     */
    public List<PolicyResponse> handleDatabaseListFailure(Throwable throwable) {
        log.error("Database circuit breaker triggered for list operation, returning empty list: {}",
                throwable.getMessage());
        return Collections.emptyList();
    }

    /**
     * Fallback for database circuit breaker in single policy retrieval.
     *
     * @param id The policy ID
     * @param throwable The exception that triggered the fallback
     * @return null to indicate the policy could not be retrieved
     */
    public PolicyResponse handleDatabaseGetFailure(UUID id, Throwable throwable) {
        log.error("Database circuit breaker triggered for policy {}, returning null: {}",
                id, throwable.getMessage());
        return null;
    }

    /**
     * Fallback for database circuit breaker in policy retrieval by tenant.
     *
     * @param tenantId The tenant ID
     * @param throwable The exception that triggered the fallback
     * @return An empty list
     */
    public List<PolicyResponse> handleDatabaseByTenantFailure(UUID tenantId, Throwable throwable) {
        log.error("Database circuit breaker triggered for tenant {}, returning empty list: {}",
                tenantId, throwable.getMessage());
        return Collections.emptyList();
    }

    /**
     * Fallback for policy resolution when database is unavailable.
     *
     * Returns null to indicate policy could not be resolved.
     * The calling code should handle this gracefully.
     *
     * @param request The rate limit request
     * @param throwable The exception that triggered the fallback
     * @return null
     */
    public Policy handlePolicyResolutionFailure(RateLimitCheckRequest request, Throwable throwable) {
        log.error("Policy resolution failed for request: {}, error: {}",
                request, throwable.getMessage());
        return null;
    }
}
