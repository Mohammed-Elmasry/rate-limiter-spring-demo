package com.example.ratelimiter.infrastructure.redis;

import com.example.ratelimiter.domain.algorithm.RateLimitResult;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.infrastructure.resilience.FallbackHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resilient wrapper for Redis operations with Circuit Breaker and Retry patterns.
 *
 * This service provides:
 * 1. Circuit Breaker protection for Redis operations
 * 2. Automatic retry on transient failures
 * 3. Fallback behavior based on Policy.failMode
 *
 * Design Rationale:
 * - Wrapping Redis calls at this level (instead of in each algorithm) provides:
 *   - Centralized resilience logic
 *   - Consistent error handling across all algorithms
 *   - Easier testing and maintenance
 *   - Single point of failure detection
 *
 * Circuit Breaker Strategy:
 * - Named "redis" to use Redis-specific configuration
 * - Fallback delegates to FallbackHandler for policy-aware decisions
 * - Retry configuration allows quick recovery from transient network issues
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientRedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final FallbackHandler fallbackHandler;

    /**
     * Executes a Redis Lua script with circuit breaker protection.
     *
     * @param script The Redis script to execute
     * @param keys The keys for the script
     * @param args The arguments for the script
     * @param key The rate limit key (for logging)
     * @param policy The policy (for fallback behavior)
     * @return The result from Redis or fallback
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "executeScriptFallback")
    @Retry(name = "redis")
    public List<Long> executeScript(
            RedisScript<List> script,
            List<String> keys,
            List<String> args,
            String key,
            Policy policy) {

        log.trace("Executing Redis script for key: {}", key);

        List<Long> result = redisTemplate.execute(
                script,
                keys,
                args.toArray()
        );

        log.trace("Redis script executed successfully for key: {}", key);
        return result;
    }

    /**
     * Fallback method when Redis circuit breaker is open or script execution fails.
     *
     * @param script The Redis script (not used in fallback)
     * @param keys The keys (not used in fallback)
     * @param args The arguments (not used in fallback)
     * @param key The rate limit key
     * @param policy The policy
     * @param throwable The exception that triggered the fallback
     * @return A result list based on the policy's fail mode
     */
    private List<Long> executeScriptFallback(
            RedisScript<List> script,
            List<String> keys,
            List<String> args,
            String key,
            Policy policy,
            Throwable throwable) {

        log.error("Redis circuit breaker triggered for key {}: {}", key, throwable.getMessage());

        // Delegate to FallbackHandler for policy-aware decision
        RateLimitResult fallbackResult = fallbackHandler.handleRedisFailure(key, policy, throwable);

        // Convert RateLimitResult to the format expected by algorithms
        // Format: [allowed (0/1), remaining, resetInSeconds]
        long allowed = fallbackResult.allowed() ? 1L : 0L;
        long remaining = fallbackResult.remaining();
        long resetInSeconds = fallbackResult.resetInSeconds();

        return List.of(allowed, remaining, resetInSeconds);
    }

    /**
     * Deletes keys matching a pattern with circuit breaker protection.
     * Used for reset operations.
     *
     * @param pattern The key pattern to match
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "deleteKeysFallback")
    @Retry(name = "redis")
    public void deleteKeys(String pattern) {
        log.debug("Deleting Redis keys matching pattern: {}", pattern);

        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Deleted {} keys matching pattern: {}", keys.size(), pattern);
        } else {
            log.debug("No keys found matching pattern: {}", pattern);
        }
    }

    /**
     * Fallback method when delete operation fails.
     * Logs the error but doesn't throw - delete failures shouldn't break the application.
     *
     * @param pattern The key pattern
     * @param throwable The exception
     */
    private void deleteKeysFallback(String pattern, Throwable throwable) {
        log.error("Failed to delete Redis keys matching pattern {}: {}",
                pattern, throwable.getMessage());
        // Don't throw - reset failures are logged but not critical
    }
}
