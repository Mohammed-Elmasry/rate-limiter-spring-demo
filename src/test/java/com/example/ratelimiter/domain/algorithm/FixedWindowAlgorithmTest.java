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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FixedWindowAlgorithm Tests")
class FixedWindowAlgorithmTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<List> fixedWindowScript;

    @Mock
    private ResilientRedisService resilientRedisService;

    private FixedWindowAlgorithm algorithm;
    private Policy testPolicy;

    @BeforeEach
    void setUp() {
        algorithm = new FixedWindowAlgorithm(redisTemplate, fixedWindowScript, resilientRedisService);

        testPolicy = Policy.builder()
                .id(UUID.randomUUID())
                .name("test-policy")
                .scope(PolicyScope.API)
                .algorithm(Algorithm.FIXED_WINDOW)
                .maxRequests(100)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Should return FIXED_WINDOW as algorithm type")
    void getType_returnsCorrectType() {
        assertThat(algorithm.getType()).isEqualTo(Algorithm.FIXED_WINDOW);
    }

    @Test
    @DisplayName("Should allow request within window")
    void execute_withinLimit_returnsAllowed() {
        // Given
        List<Long> scriptResult = Arrays.asList(1L, 99L, 60L);
        when(resilientRedisService.executeScript(any(), anyList(), anyList(), anyString(), any()))
                .thenReturn(scriptResult);

        // When
        RateLimitResult result = algorithm.execute("api_key_123", testPolicy);

        // Then
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Should deny request when window exceeded")
    void execute_exceedsLimit_returnsDenied() {
        // Given
        List<Long> scriptResult = Arrays.asList(0L, 0L, 30L);
        when(resilientRedisService.executeScript(any(), anyList(), anyList(), anyString(), any()))
                .thenReturn(scriptResult);

        // When
        RateLimitResult result = algorithm.execute("api_key_123", testPolicy);

        // Then
        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0L);
        assertThat(result.retryAfter()).isEqualTo(30L);
    }

    @Test
    @DisplayName("Should throw exception for null key")
    void execute_nullKey_throwsException() {
        assertThatThrownBy(() -> algorithm.execute(null, testPolicy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw exception for null policy")
    void execute_nullPolicy_throwsException() {
        assertThatThrownBy(() -> algorithm.execute("key", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reset window state")
    void reset_validKey_deletesKeys() {
        // When
        algorithm.reset("api_key_123");

        // Then
        verify(resilientRedisService).deleteKeys("rl:fixed:*:api_key_123:*");
    }
}
