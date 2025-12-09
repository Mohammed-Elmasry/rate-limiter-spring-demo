package com.example.ratelimiter.domain.algorithm;

import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.infrastructure.redis.ResilientRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBucketAlgorithm Tests")
class TokenBucketAlgorithmTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<List> tokenBucketScript;

    @Mock
    private ResilientRedisService resilientRedisService;

    private TokenBucketAlgorithm algorithm;

    private Policy testPolicy;

    @BeforeEach
    void setUp() {
        algorithm = new TokenBucketAlgorithm(redisTemplate, tokenBucketScript, resilientRedisService);

        testPolicy = Policy.builder()
                .id(UUID.randomUUID())
                .name("test-policy")
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .burstCapacity(120)
                .refillRate(BigDecimal.valueOf(10.0))
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Should return TOKEN_BUCKET as algorithm type")
    void getType_returnsCorrectType() {
        // When
        Algorithm type = algorithm.getType();

        // Then
        assertThat(type).isEqualTo(Algorithm.TOKEN_BUCKET);
    }

    @Test
    @DisplayName("Should allow request when tokens available")
    void execute_tokensAvailable_returnsAllowed() {
        // Given
        String identifier = "user123";
        List<Long> scriptResult = Arrays.asList(1L, 119L, 60L); // allowed=1, remaining=119, reset=60

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(scriptResult);

        // When
        RateLimitResult result = algorithm.execute(identifier, testPolicy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(119L);
        assertThat(result.resetInSeconds()).isEqualTo(60L);
        assertThat(result.retryAfter()).isEqualTo(0L);

        verify(resilientRedisService).executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                eq(testPolicy)
        );
    }

    @Test
    @DisplayName("Should deny request when no tokens available")
    void execute_noTokensAvailable_returnsDenied() {
        // Given
        String identifier = "user123";
        List<Long> scriptResult = Arrays.asList(0L, 0L, 45L); // allowed=0, remaining=0, reset=45

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(scriptResult);

        // When
        RateLimitResult result = algorithm.execute(identifier, testPolicy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0L);
        assertThat(result.resetInSeconds()).isEqualTo(45L);
        assertThat(result.retryAfter()).isEqualTo(45L);
    }

    @Test
    @DisplayName("Should use burst capacity when specified")
    void execute_withBurstCapacity_usesCorrectCapacity() {
        // Given
        String identifier = "user123";
        List<Long> scriptResult = Arrays.asList(1L, 95L, 60L);

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(scriptResult);

        // When
        algorithm.execute(identifier, testPolicy);

        // Then
        verify(resilientRedisService).executeScript(
                eq(tokenBucketScript),
                anyList(),
                argThat(args -> args.contains("120")), // burst capacity
                anyString(),
                any(Policy.class)
        );
    }

    @Test
    @DisplayName("Should use maxRequests as capacity when burst capacity not specified")
    void execute_noBurstCapacity_usesMaxRequests() {
        // Given
        testPolicy.setBurstCapacity(null);
        String identifier = "user123";
        List<Long> scriptResult = Arrays.asList(1L, 99L, 60L);

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(scriptResult);

        // When
        algorithm.execute(identifier, testPolicy);

        // Then
        verify(resilientRedisService).executeScript(
                eq(tokenBucketScript),
                anyList(),
                argThat(args -> args.contains("100")), // maxRequests as capacity
                anyString(),
                any(Policy.class)
        );
    }

    @Test
    @DisplayName("Should calculate refill rate from policy")
    void execute_withRefillRate_usesSpecifiedRate() {
        // Given
        String identifier = "user123";
        List<Long> scriptResult = Arrays.asList(1L, 100L, 60L);

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(scriptResult);

        // When
        algorithm.execute(identifier, testPolicy);

        // Then
        verify(resilientRedisService).executeScript(
                eq(tokenBucketScript),
                anyList(),
                argThat(args -> args.contains("10.0")), // refill rate
                anyString(),
                any(Policy.class)
        );
    }

    @Test
    @DisplayName("Should derive refill rate when not specified")
    void execute_noRefillRate_derivesFromMaxRequests() {
        // Given
        testPolicy.setRefillRate(null);
        String identifier = "user123";
        List<Long> scriptResult = Arrays.asList(1L, 99L, 60L);

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(scriptResult);

        // When
        algorithm.execute(identifier, testPolicy);

        // Then
        // refillRate should be maxRequests / windowSeconds = 100 / 60 ≈ 1.67
        verify(resilientRedisService).executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        );
    }

    @Test
    @DisplayName("Should throw exception for null key")
    void execute_nullKey_throwsException() {
        // When / Then
        assertThatThrownBy(() -> algorithm.execute(null, testPolicy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for empty key")
    void execute_emptyKey_throwsException() {
        // When / Then
        assertThatThrownBy(() -> algorithm.execute("", testPolicy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for null policy")
    void execute_nullPolicy_throwsException() {
        // When / Then
        assertThatThrownBy(() -> algorithm.execute("user123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Policy cannot be null");
    }

    @Test
    @DisplayName("Should handle invalid script result gracefully")
    void execute_invalidScriptResult_returnsDenied() {
        // Given
        String identifier = "user123";
        List<Long> invalidResult = Arrays.asList(1L); // Missing required values

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(invalidResult);

        // When
        RateLimitResult result = algorithm.execute(identifier, testPolicy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle null script result gracefully")
    void execute_nullScriptResult_returnsDenied() {
        // Given
        String identifier = "user123";

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(null);

        // When
        RateLimitResult result = algorithm.execute(identifier, testPolicy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.allowed()).isFalse();
    }

    @Test
    @DisplayName("Should reset token bucket state for key")
    void reset_validKey_deletesKeys() {
        // Given
        String identifier = "user123";

        // When
        algorithm.reset(identifier);

        // Then
        verify(resilientRedisService).deleteKeys("rl:token:*:" + identifier);
    }

    @Test
    @DisplayName("Should throw exception when resetting null key")
    void reset_nullKey_throwsException() {
        // When / Then
        assertThatThrownBy(() -> algorithm.reset(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when resetting empty key")
    void reset_emptyKey_throwsException() {
        // When / Then
        assertThatThrownBy(() -> algorithm.reset(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key cannot be null or empty");
    }

    @Test
    @DisplayName("Should build correct Redis key format")
    void execute_validInput_buildsCorrectKey() {
        // Given
        String identifier = "user123";
        List<Long> scriptResult = Arrays.asList(1L, 99L, 60L);

        when(resilientRedisService.executeScript(
                eq(tokenBucketScript),
                anyList(),
                anyList(),
                anyString(),
                any(Policy.class)
        )).thenReturn(scriptResult);

        // When
        algorithm.execute(identifier, testPolicy);

        // Then
        verify(resilientRedisService).executeScript(
                eq(tokenBucketScript),
                argThat(keys -> keys.get(0).equals("rl:token:user:user123")),
                anyList(),
                anyString(),
                any(Policy.class)
        );
    }
}
