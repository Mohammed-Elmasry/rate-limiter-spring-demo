package com.example.ratelimiter.infrastructure.redis;

import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.Algorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * @deprecated This class is deprecated in favor of the Strategy pattern implementation.
 * Use {@link com.example.ratelimiter.domain.algorithm.AlgorithmFactory} and
 * {@link com.example.ratelimiter.domain.algorithm.RateLimitAlgorithm} instead.
 * This class remains for backward compatibility and will be removed in a future version.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
@Component
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimiter {

    private static final String KEY_PREFIX = "rl";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> tokenBucketScript;
    private final RedisScript<List> fixedWindowScript;
    private final RedisScript<List> slidingLogScript;

    public RateLimitResult checkRateLimit(String identifier, Policy policy) {
        return switch (policy.getAlgorithm()) {
            case TOKEN_BUCKET -> executeTokenBucket(identifier, policy);
            case FIXED_WINDOW -> executeFixedWindow(identifier, policy);
            case SLIDING_LOG -> executeSlidingLog(identifier, policy);
        };
    }

    private RateLimitResult executeTokenBucket(String identifier, Policy policy) {
        String key = buildKey("token", identifier, policy);
        long nowMs = Instant.now().toEpochMilli();

        int capacity = policy.getBurstCapacity() != null
                ? policy.getBurstCapacity()
                : policy.getMaxRequests();

        double refillRate = policy.getRefillRate() != null
                ? policy.getRefillRate().doubleValue()
                : (double) policy.getMaxRequests() / policy.getWindowSeconds();

        int ttl = policy.getWindowSeconds() * 2;

        List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                Collections.singletonList(key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(nowMs),
                "1",
                String.valueOf(ttl)
        );

        return parseResult(result);
    }

    private RateLimitResult executeFixedWindow(String identifier, Policy policy) {
        String keyPrefix = buildKey("fixed", identifier, policy);
        long nowSec = Instant.now().getEpochSecond();

        List<Long> result = redisTemplate.execute(
                fixedWindowScript,
                Collections.singletonList(keyPrefix),
                String.valueOf(policy.getMaxRequests()),
                String.valueOf(policy.getWindowSeconds()),
                String.valueOf(nowSec),
                "1"
        );

        return parseResult(result);
    }

    private RateLimitResult executeSlidingLog(String identifier, Policy policy) {
        String key = buildKey("sliding", identifier, policy);
        long nowMs = Instant.now().toEpochMilli();
        long windowMs = (long) policy.getWindowSeconds() * 1000;
        int ttl = policy.getWindowSeconds() * 2;

        List<Long> result = redisTemplate.execute(
                slidingLogScript,
                Collections.singletonList(key),
                String.valueOf(policy.getMaxRequests()),
                String.valueOf(windowMs),
                String.valueOf(nowMs),
                "1",
                String.valueOf(ttl)
        );

        return parseResult(result);
    }

    private String buildKey(String algorithm, String identifier, Policy policy) {
        return String.format("%s:%s:%s:%s",
                KEY_PREFIX,
                algorithm,
                policy.getScope().name().toLowerCase(),
                identifier);
    }

    private RateLimitResult parseResult(List<Long> result) {
        if (result == null || result.size() < 3) {
            log.warn("Invalid result from Redis script: {}", result);
            return RateLimitResult.builder()
                    .allowed(false)
                    .remaining(0)
                    .resetInSeconds(0)
                    .build();
        }

        return RateLimitResult.builder()
                .allowed(result.get(0) == 1)
                .remaining(result.get(1))
                .resetInSeconds(result.get(2))
                .build();
    }
}
