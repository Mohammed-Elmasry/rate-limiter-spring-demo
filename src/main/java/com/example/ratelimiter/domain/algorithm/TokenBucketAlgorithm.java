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
 * Token Bucket algorithm implementation.
 *
 * The token bucket algorithm provides smooth rate limiting with burst capacity.
 * Tokens are added to a bucket at a constant rate, and each request consumes one token.
 * If the bucket is full, new tokens are discarded.
 *
 * Key characteristics:
 * - Allows bursts up to the bucket capacity
 * - Smooths out traffic over time via constant refill rate
 * - Good for APIs that need to handle occasional spikes
 *
 * Redis Implementation:
 * - Uses a hash to store tokens and last_refill timestamp
 * - Atomic execution via Lua script ensures consistency
 * - Calculates refill based on elapsed time since last request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl";
    private static final String ALGORITHM_NAME = "token";
    private static final int DEFAULT_TOKENS_PER_REQUEST = 1;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> tokenBucketScript;
    private final ResilientRedisService resilientRedisService;

    @Override
    public RateLimitResult execute(String key, Policy policy) {
        validateInput(key, policy);

        String redisKey = buildKey(key, policy);
        long nowMs = Instant.now().toEpochMilli();

        // Determine bucket capacity (prefer burstCapacity, fallback to maxRequests)
        int capacity = policy.getBurstCapacity() != null
                ? policy.getBurstCapacity()
                : policy.getMaxRequests();

        // Calculate refill rate (tokens per second)
        // If specified in policy, use it; otherwise derive from maxRequests/windowSeconds
        double refillRate = policy.getRefillRate() != null
                ? policy.getRefillRate().doubleValue()
                : (double) policy.getMaxRequests() / policy.getWindowSeconds();

        // TTL ensures cleanup of inactive keys (2x window for safety)
        int ttl = policy.getWindowSeconds() * 2;

        log.debug("Executing token bucket: key={}, capacity={}, refillRate={}, now={}",
                redisKey, capacity, refillRate, nowMs);

        List<Long> result = resilientRedisService.executeScript(
                tokenBucketScript,
                Collections.singletonList(redisKey),
                List.of(
                        String.valueOf(capacity),
                        String.valueOf(refillRate),
                        String.valueOf(nowMs),
                        String.valueOf(DEFAULT_TOKENS_PER_REQUEST),
                        String.valueOf(ttl)
                ),
                redisKey,
                policy
        );

        return parseResult(result);
    }

    @Override
    public Algorithm getType() {
        return Algorithm.TOKEN_BUCKET;
    }

    @Override
    public void reset(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        // For token bucket, we need to delete keys with wildcard pattern
        // since the full key includes policy scope information
        String pattern = KEY_PREFIX + ":" + ALGORITHM_NAME + ":*:" + key;
        resilientRedisService.deleteKeys(pattern);

        log.info("Reset token bucket state for key: {}", key);
    }

    /**
     * Builds the Redis key for token bucket storage.
     *
     * Key format: rl:token:{scope}:{identifier}
     * Example: rl:token:user:john_doe
     */
    private String buildKey(String identifier, Policy policy) {
        return String.format("%s:%s:%s:%s",
                KEY_PREFIX,
                ALGORITHM_NAME,
                policy.getScope().name().toLowerCase(),
                identifier);
    }

    /**
     * Parses the Lua script result into a RateLimitResult.
     *
     * Lua script returns: {allowed (0/1), remaining tokens, reset time in seconds}
     */
    private RateLimitResult parseResult(List<Long> result) {
        if (result == null || result.size() < 3) {
            log.warn("Invalid result from token bucket script: {}", result);
            return RateLimitResult.denied(0, 0, 0);
        }

        boolean allowed = result.get(0) == 1;
        long remaining = result.get(1);
        long resetInSeconds = result.get(2);

        if (allowed) {
            return RateLimitResult.allowed(remaining, resetInSeconds);
        } else {
            // For denied requests, retryAfter equals resetTime (when tokens will be available)
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
