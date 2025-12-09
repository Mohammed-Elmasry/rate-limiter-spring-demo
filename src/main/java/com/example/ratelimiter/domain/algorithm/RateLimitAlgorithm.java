package com.example.ratelimiter.domain.algorithm;

import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.Algorithm;

/**
 * Strategy interface for rate limiting algorithms.
 *
 * This interface defines the contract for all rate limiting algorithm implementations,
 * following the Strategy design pattern. Each implementation encapsulates a specific
 * rate limiting algorithm (Token Bucket, Fixed Window, Sliding Log).
 *
 * Thread Safety: Implementations must be thread-safe as they will be used as singleton beans.
 */
public interface RateLimitAlgorithm {

    /**
     * Executes the rate limit check for a given key and policy.
     *
     * This method is the core of the strategy pattern. It attempts to consume a request
     * from the rate limiter and returns the result indicating whether the request is allowed.
     *
     * @param key the unique identifier for rate limiting (e.g., user ID, IP address, API key)
     * @param policy the policy configuration containing limits and algorithm parameters
     * @return the result of the rate limit check, including whether allowed and remaining quota
     * @throws IllegalArgumentException if key is null or empty, or if policy is null
     */
    RateLimitResult execute(String key, Policy policy);

    /**
     * Returns the algorithm type this implementation handles.
     *
     * This is used by the AlgorithmFactory to route requests to the correct implementation.
     *
     * @return the algorithm type enum value
     */
    Algorithm getType();

    /**
     * Resets the rate limit state for a specific key.
     *
     * This operation is useful for administrative operations or testing scenarios
     * where you need to clear the rate limit state for a specific identifier.
     *
     * @param key the unique identifier to reset
     * @throws IllegalArgumentException if key is null or empty
     */
    void reset(String key);
}
