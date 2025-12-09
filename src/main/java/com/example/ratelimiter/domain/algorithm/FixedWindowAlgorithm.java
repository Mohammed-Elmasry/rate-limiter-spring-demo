package com.example.ratelimiter.domain.algorithm;

import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.infrastructure.redis.ResilientRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Fixed Window algorithm implementation.
 *
 * The fixed window algorithm divides time into fixed-size windows and limits
 * requests within each window. When a window expires, the counter resets.
 *
 * Key characteristics:
 * - Simple and memory efficient
 * - Predictable reset times
 * - Susceptible to burst at window boundaries (double the limit in 2x window duration)
 *
 * Example: 10 requests per 60 seconds
 * - Window 1: 00:00-00:59 (10 requests allowed)
 * - Window 2: 01:00-01:59 (10 requests allowed)
 * - Edge case: 9 requests at 00:59 + 10 requests at 01:00 = 19 requests in 1 second
 *
 * Redis Implementation:
 * - Uses a simple counter with window ID in the key
 * - Window ID = floor(current_time / window_size)
 * - Each window gets its own key with TTL for automatic cleanup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl";
    private static final String ALGORITHM_NAME = "fixed";
    private static final int DEFAULT_INCREMENT = 1;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> fixedWindowScript;
    private final ResilientRedisService resilientRedisService;

    @Override
    public RateLimitResult execute(String key, Policy policy) {
        validateInput(key, policy);

        String keyPrefix = buildKeyPrefix(key, policy);
        long nowSec = Instant.now().getEpochSecond();

        log.debug("Executing fixed window: keyPrefix={}, maxRequests={}, windowSeconds={}, now={}",
                keyPrefix, policy.getMaxRequests(), policy.getWindowSeconds(), nowSec);

        List<Long> result = resilientRedisService.executeScript(
                fixedWindowScript,
                Collections.singletonList(keyPrefix),
                List.of(
                        String.valueOf(policy.getMaxRequests()),
                        String.valueOf(policy.getWindowSeconds()),
                        String.valueOf(nowSec),
                        String.valueOf(DEFAULT_INCREMENT)
                ),
                keyPrefix,
                policy
        );

        return parseResult(result);
    }

    @Override
    public Algorithm getType() {
        return Algorithm.FIXED_WINDOW;
    }

    @Override
    public void reset(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        // Fixed window uses keys with window ID suffix (e.g., rl:fixed:user:john:12345)
        // We need to delete all windows for this key
        String pattern = KEY_PREFIX + ":" + ALGORITHM_NAME + ":*:" + key + ":*";
        resilientRedisService.deleteKeys(pattern);

        log.info("Reset fixed window state for key: {}", key);
    }

    /**
     * Builds the Redis key prefix for fixed window storage.
     *
     * Key format: rl:fixed:{scope}:{identifier}
     * The Lua script appends the window ID to create the full key
     * Example: rl:fixed:user:john_doe:12345 (where 12345 is the window ID)
     */
    private String buildKeyPrefix(String identifier, Policy policy) {
        return String.format("%s:%s:%s:%s",
                KEY_PREFIX,
                ALGORITHM_NAME,
                policy.getScope().name().toLowerCase(),
                identifier);
    }

    /**
     * Parses the Lua script result into a RateLimitResult.
     *
     * Lua script returns: {allowed (0/1), remaining requests, reset time in seconds}
     */
    private RateLimitResult parseResult(List<Long> result) {
        if (result == null || result.size() < 3) {
            log.warn("Invalid result from fixed window script: {}", result);
            return RateLimitResult.denied(0, 0, 0);
        }

        boolean allowed = result.get(0) == 1;
        long remaining = result.get(1);
        long resetInSeconds = result.get(2);

        if (allowed) {
            return RateLimitResult.allowed(remaining, resetInSeconds);
        } else {
            // For denied requests, client should retry after the current window expires
            return RateLimitResult.denied(remaining, resetInSeconds, resetInSeconds);
        }
    }

    private void validateInput(String key, Policy policy) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (policy == null) {
            throw new IllegalArgumentException("Policy cannot be null");
        }
    }
}
