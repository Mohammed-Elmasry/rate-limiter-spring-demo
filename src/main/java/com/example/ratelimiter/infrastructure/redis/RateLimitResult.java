package com.example.ratelimiter.infrastructure.redis;

import lombok.Builder;
import lombok.Value;

/**
 * @deprecated This class has been moved to the domain layer.
 * Use {@link com.example.ratelimiter.domain.algorithm.RateLimitResult} instead.
 * This class remains for backward compatibility with RedisRateLimiter
 * and will be removed in a future version.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
@Value
@Builder
public class RateLimitResult {
    boolean allowed;
    long remaining;
    long resetInSeconds;
}
