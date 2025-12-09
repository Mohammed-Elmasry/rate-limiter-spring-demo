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
 * Sliding Log (Sliding Window) algorithm implementation.
 *
 * The sliding log algorithm maintains a timestamp for each request in a rolling time window.
 * It provides the most accurate rate limiting by counting requests in the exact time window
 * from the current moment backwards.
 *
 * Key characteristics:
 * - Most accurate: no burst issues at window boundaries
 * - Memory intensive: stores a log entry for each request
 * - Best for scenarios requiring strict rate limiting guarantees
 *
 * Example: 10 requests per 60 seconds
 * - At time T, count all requests between (T-60s) and T
 * - Automatically removes expired entries
 * - No boundary issue unlike fixed window
 *
 * Redis Implementation:
 * - Uses a sorted set (ZSET) with timestamps as scores
 * - Each request is a member with timestamp score
 * - ZREMRANGEBYSCORE removes expired entries
 * - ZCARD counts active requests in the window
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlidingLogAlgorithm implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl";
    private static final String ALGORITHM_NAME = "sliding";
    private static final int DEFAULT_INCREMENT = 1;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> slidingLogScript;
    private final ResilientRedisService resilientRedisService;

    @Override
    public RateLimitResult execute(String key, Policy policy) {
        validateInput(key, policy);

        String redisKey = buildKey(key, policy);
        long nowMs = Instant.now().toEpochMilli();
        long windowMs = (long) policy.getWindowSeconds() * 1000;

        // TTL ensures cleanup of inactive keys (2x window for safety)
        int ttl = policy.getWindowSeconds() * 2;

        log.debug("Executing sliding log: key={}, maxRequests={}, windowMs={}, now={}",
                redisKey, policy.getMaxRequests(), windowMs, nowMs);

        List<Long> result = resilientRedisService.executeScript(
                slidingLogScript,
                Collections.singletonList(redisKey),
                List.of(
                        String.valueOf(policy.getMaxRequests()),
                        String.valueOf(windowMs),
                        String.valueOf(nowMs),
                        String.valueOf(DEFAULT_INCREMENT),
                        String.valueOf(ttl)
                ),
                redisKey,
                policy
        );

        return parseResult(result);
    }

    @Override
    public Algorithm getType() {
        return Algorithm.SLIDING_LOG;
    }

    @Override
    public void reset(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        // For sliding log, we need to delete keys with wildcard pattern
        // since the full key includes policy scope information
        String pattern = KEY_PREFIX + ":" + ALGORITHM_NAME + ":*:" + key;
        resilientRedisService.deleteKeys(pattern);

        log.info("Reset sliding log state for key: {}", key);
    }

    /**
     * Builds the Redis key for sliding log storage.
     *
     * Key format: rl:sliding:{scope}:{identifier}
     * Example: rl:sliding:user:john_doe
     *
     * The key points to a ZSET containing timestamps of all requests in the window.
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
     * Lua script returns: {allowed (0/1), remaining requests, reset time in seconds}
     *
     * Reset time for sliding log represents when the oldest entry expires,
     * allowing a new request to be accepted.
     */
    private RateLimitResult parseResult(List<Long> result) {
        if (result == null || result.size() < 3) {
            log.warn("Invalid result from sliding log script: {}", result);
            return RateLimitResult.denied(0, 0, 0);
        }

        boolean allowed = result.get(0) == 1;
        long remaining = result.get(1);
        long resetInSeconds = result.get(2);

        if (allowed) {
            return RateLimitResult.allowed(remaining, resetInSeconds);
        } else {
            // For denied requests, retryAfter is when the oldest entry expires
            // This is when a new request slot becomes available
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
