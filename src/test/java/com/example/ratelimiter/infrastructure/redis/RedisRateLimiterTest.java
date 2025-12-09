package com.example.ratelimiter.infrastructure.redis;

import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<List> tokenBucketScript;

    @Mock
    private RedisScript<List> fixedWindowScript;

    @Mock
    private RedisScript<List> slidingLogScript;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisRateLimiter(
                redisTemplate,
                tokenBucketScript,
                fixedWindowScript,
                slidingLogScript
        );
    }

    private Policy createPolicy(Algorithm algorithm, int maxRequests, int windowSeconds) {
        return Policy.builder()
                .id(UUID.randomUUID())
                .name("test-policy")
                .scope(PolicyScope.USER)
                .algorithm(algorithm)
                .maxRequests(maxRequests)
                .windowSeconds(windowSeconds)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("Token Bucket Algorithm")
    class TokenBucketTests {

        @Test
        @DisplayName("should allow requests within bucket capacity")
        void shouldAllowRequestsWithinCapacity() {
            Policy policy = createPolicy(Algorithm.TOKEN_BUCKET, 10, 60);
            policy.setBurstCapacity(10);
            policy.setRefillRate(BigDecimal.valueOf(0.166));

            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [allowed (1), remaining (9), reset (60)]
            when(redisTemplate.execute(
                    eq(tokenBucketScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(1L, 9L, 60L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(9);
            assertThat(result.getResetInSeconds()).isEqualTo(60);

            // Verify the script was called with correct parameters
            verify(redisTemplate).execute(
                    eq(tokenBucketScript),
                    anyList(),
                    eq("10"),           // capacity
                    eq("0.166"),        // refill rate
                    anyString(),        // current time
                    eq("1"),            // cost
                    eq("120")           // ttl (windowSeconds * 2)
            );
        }

        @Test
        @DisplayName("should deny requests when bucket is empty")
        void shouldDenyWhenBucketEmpty() {
            Policy policy = createPolicy(Algorithm.TOKEN_BUCKET, 3, 60);
            policy.setBurstCapacity(3);
            policy.setRefillRate(BigDecimal.valueOf(0.05));

            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [denied (0), remaining (0), reset (20)]
            when(redisTemplate.execute(
                    eq(tokenBucketScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(0L, 0L, 20L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemaining()).isEqualTo(0);
            assertThat(result.getResetInSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should use default refill rate when not specified")
        void shouldUseDefaultRefillRate() {
            Policy policy = createPolicy(Algorithm.TOKEN_BUCKET, 60, 60);
            // No burst capacity or refill rate set - should default to maxRequests/windowSeconds

            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [allowed (1), remaining (59), reset (60)]
            when(redisTemplate.execute(
                    eq(tokenBucketScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(1L, 59L, 60L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(59);

            // Verify default refill rate calculation: 60/60 = 1.0
            verify(redisTemplate).execute(
                    eq(tokenBucketScript),
                    anyList(),
                    eq("60"),           // capacity (defaults to maxRequests)
                    eq("1.0"),          // refill rate (maxRequests/windowSeconds)
                    anyString(),
                    eq("1"),
                    eq("120")
            );
        }

        @Test
        @DisplayName("should handle invalid Redis response gracefully")
        void shouldHandleInvalidResponse() {
            Policy policy = createPolicy(Algorithm.TOKEN_BUCKET, 10, 60);
            String identifier = "user-" + UUID.randomUUID();

            // Mock null response from Redis (connection issue)
            when(redisTemplate.execute(
                    eq(tokenBucketScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(null);

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            // Should fail closed
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemaining()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Fixed Window Algorithm")
    class FixedWindowTests {

        @Test
        @DisplayName("should allow requests within window limit")
        void shouldAllowRequestsWithinLimit() {
            Policy policy = createPolicy(Algorithm.FIXED_WINDOW, 5, 60);
            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [allowed (1), remaining (4), reset (45)]
            when(redisTemplate.execute(
                    eq(fixedWindowScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(1L, 4L, 45L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(4);

            // Verify the script was called with correct parameters
            verify(redisTemplate).execute(
                    eq(fixedWindowScript),
                    anyList(),
                    eq("5"),            // max requests
                    eq("60"),           // window seconds
                    anyString(),        // current time
                    eq("1")             // cost
            );
        }

        @Test
        @DisplayName("should deny requests exceeding window limit")
        void shouldDenyRequestsExceedingLimit() {
            Policy policy = createPolicy(Algorithm.FIXED_WINDOW, 3, 60);
            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [denied (0), remaining (0), reset (30)]
            when(redisTemplate.execute(
                    eq(fixedWindowScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(0L, 0L, 30L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemaining()).isEqualTo(0);
        }

        @Test
        @DisplayName("should provide reset time until window ends")
        void shouldProvideResetTime() {
            Policy policy = createPolicy(Algorithm.FIXED_WINDOW, 1, 60);
            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [denied (0), remaining (0), reset (45)]
            when(redisTemplate.execute(
                    eq(fixedWindowScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(0L, 0L, 45L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getResetInSeconds()).isEqualTo(45);
        }
    }

    @Nested
    @DisplayName("Sliding Log Algorithm")
    class SlidingLogTests {

        @Test
        @DisplayName("should allow requests within window limit")
        void shouldAllowRequestsWithinLimit() {
            Policy policy = createPolicy(Algorithm.SLIDING_LOG, 5, 60);
            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [allowed (1), remaining (4), reset (60)]
            when(redisTemplate.execute(
                    eq(slidingLogScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(1L, 4L, 60L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(4);

            // Verify the script was called with correct parameters
            verify(redisTemplate).execute(
                    eq(slidingLogScript),
                    anyList(),
                    eq("5"),            // max requests
                    eq("60000"),        // window in milliseconds
                    anyString(),        // current time
                    eq("1"),            // cost
                    eq("120")           // ttl
            );
        }

        @Test
        @DisplayName("should deny requests exceeding window limit")
        void shouldDenyRequestsExceedingLimit() {
            Policy policy = createPolicy(Algorithm.SLIDING_LOG, 3, 60);
            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [denied (0), remaining (0), reset (30)]
            when(redisTemplate.execute(
                    eq(slidingLogScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(0L, 0L, 30L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemaining()).isEqualTo(0);
        }

        @Test
        @DisplayName("should track requests precisely with sliding window")
        void shouldTrackPrecisely() {
            Policy policy = createPolicy(Algorithm.SLIDING_LOG, 5, 60);
            String identifier = "user-" + UUID.randomUUID();

            // Mock Redis response: [allowed (1), remaining (1), reset (60)]
            when(redisTemplate.execute(
                    eq(slidingLogScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(1L, 1L, 60L));

            RateLimitResult result = rateLimiter.checkRateLimit(identifier, policy);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Cross-Algorithm Tests")
    class CrossAlgorithmTests {

        @Test
        @DisplayName("different identifiers should have independent limits")
        void differentIdentifiersShouldBeIndependent() {
            Policy policy = createPolicy(Algorithm.FIXED_WINDOW, 2, 60);

            String user1 = "user-1";
            String user2 = "user-2";

            // Mock first call for user1 (exhausted)
            when(redisTemplate.execute(
                    eq(fixedWindowScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(0L, 0L, 45L))
              .thenReturn(Arrays.asList(1L, 1L, 45L));

            RateLimitResult result1 = rateLimiter.checkRateLimit(user1, policy);
            assertThat(result1.isAllowed()).isFalse();

            RateLimitResult result2 = rateLimiter.checkRateLimit(user2, policy);
            assertThat(result2.isAllowed()).isTrue();
            assertThat(result2.getRemaining()).isEqualTo(1);
        }

        @Test
        @DisplayName("different policies should have independent limits")
        void differentPoliciesShouldBeIndependent() {
            Policy policy1 = createPolicy(Algorithm.FIXED_WINDOW, 1, 60);
            Policy policy2 = createPolicy(Algorithm.FIXED_WINDOW, 5, 60);

            String identifier = "user-test";

            // Mock sequential responses
            when(redisTemplate.execute(
                    eq(fixedWindowScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(0L, 0L, 45L))  // First call returns exhausted
              .thenReturn(Arrays.asList(1L, 4L, 45L)); // Second call returns allowed

            RateLimitResult result1 = rateLimiter.checkRateLimit(identifier, policy1);
            assertThat(result1.isAllowed()).isFalse();

            RateLimitResult result2 = rateLimiter.checkRateLimit(identifier, policy2);
            assertThat(result2.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Key Building Tests")
    class KeyBuildingTests {

        @Test
        @DisplayName("should build correct key format for token bucket")
        void shouldBuildCorrectKeyForTokenBucket() {
            Policy policy = createPolicy(Algorithm.TOKEN_BUCKET, 10, 60);
            String identifier = "test-user";

            when(redisTemplate.execute(
                    eq(tokenBucketScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(1L, 9L, 60L));

            rateLimiter.checkRateLimit(identifier, policy);

            // Capture the key argument
            ArgumentCaptor<List> keyCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisTemplate).execute(
                    eq(tokenBucketScript),
                    keyCaptor.capture(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            );

            String key = (String) keyCaptor.getValue().get(0);
            assertThat(key).startsWith("rl:token:user:");
            assertThat(key).contains(identifier);
        }

        @Test
        @DisplayName("should build correct key format for fixed window")
        void shouldBuildCorrectKeyForFixedWindow() {
            Policy policy = createPolicy(Algorithm.FIXED_WINDOW, 5, 60);
            String identifier = "test-user";

            when(redisTemplate.execute(
                    eq(fixedWindowScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(1L, 4L, 45L));

            rateLimiter.checkRateLimit(identifier, policy);

            ArgumentCaptor<List> keyCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisTemplate).execute(
                    eq(fixedWindowScript),
                    keyCaptor.capture(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            );

            String key = (String) keyCaptor.getValue().get(0);
            assertThat(key).startsWith("rl:fixed:user:");
            assertThat(key).contains(identifier);
        }

        @Test
        @DisplayName("should build correct key format for sliding log")
        void shouldBuildCorrectKeyForSlidingLog() {
            Policy policy = createPolicy(Algorithm.SLIDING_LOG, 5, 60);
            String identifier = "test-user";

            when(redisTemplate.execute(
                    eq(slidingLogScript),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(Arrays.asList(1L, 4L, 60L));

            rateLimiter.checkRateLimit(identifier, policy);

            ArgumentCaptor<List> keyCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisTemplate).execute(
                    eq(slidingLogScript),
                    keyCaptor.capture(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString()
            );

            String key = (String) keyCaptor.getValue().get(0);
            assertThat(key).startsWith("rl:sliding:user:");
            assertThat(key).contains(identifier);
        }
    }
}
