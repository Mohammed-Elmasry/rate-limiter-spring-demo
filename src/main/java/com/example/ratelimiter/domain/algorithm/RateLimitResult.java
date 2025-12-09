package com.example.ratelimiter.domain.algorithm;

/**
 * Value object representing the result of a rate limit check.
 *
 * This immutable record encapsulates all information returned by a rate limiting
 * algorithm execution. It provides both the decision (allowed/denied) and metadata
 * about the current rate limit state.
 *
 * Using a record ensures immutability, automatic equals/hashCode/toString,
 * and clear intent that this is a value object with no behavior.
 *
 * @param allowed whether the request is allowed (true) or denied (false)
 * @param remaining number of requests remaining in the current window
 * @param resetInSeconds time in seconds until the rate limit resets and requests are allowed again
 * @param retryAfterSeconds time in seconds the client should wait before retrying (0 if allowed)
 */
public record RateLimitResult(
        boolean allowed,
        long remaining,
        long resetInSeconds,
        long retryAfterSeconds
) {

    /**
     * Creates a result for an allowed request.
     *
     * @param remaining number of requests remaining
     * @param resetInSeconds time until reset
     * @return a RateLimitResult indicating the request is allowed
     */
    public static RateLimitResult allowed(long remaining, long resetInSeconds) {
        return new RateLimitResult(true, remaining, resetInSeconds, 0);
    }

    /**
     * Creates a result for a denied request.
     *
     * @param remaining number of requests remaining (typically 0)
     * @param resetInSeconds time until reset
     * @param retryAfterSeconds time to wait before retry
     * @return a RateLimitResult indicating the request is denied
     */
    public static RateLimitResult denied(long remaining, long resetInSeconds, long retryAfterSeconds) {
        return new RateLimitResult(false, remaining, resetInSeconds, retryAfterSeconds);
    }

    /**
     * Compact constructor with validation.
     */
    public RateLimitResult {
        if (remaining < 0) {
            remaining = 0;
        }
        if (resetInSeconds < 0) {
            resetInSeconds = 0;
        }
        if (retryAfterSeconds < 0) {
            retryAfterSeconds = 0;
        }
    }
}
