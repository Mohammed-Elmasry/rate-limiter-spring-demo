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
@DisplayName("SlidingLogAlgorithm Tests")
class SlidingLogAlgorithmTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<List> slidingLogScript;

    @Mock
    private ResilientRedisService resilientRedisService;

    private SlidingLogAlgorithm algorithm;
    private Policy testPolicy;

    @BeforeEach
    void setUp() {
        algorithm = new SlidingLogAlgorithm(redisTemplate, slidingLogScript, resilientRedisService);

        testPolicy = Policy.builder()
                .id(UUID.randomUUID())
                .name("test-policy")
                .scope(PolicyScope.IP)
                .algorithm(Algorithm.SLIDING_LOG)
                .maxRequests(50)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Should return SLIDING_LOG as algorithm type")
    void getType_returnsCorrectType() {
        assertThat(algorithm.getType()).isEqualTo(Algorithm.SLIDING_LOG);
    }

    @Test
    @DisplayName("Should allow request within sliding window")
    void execute_withinLimit_returnsAllowed() {
        // Given
        List<Long> scriptResult = Arrays.asList(1L, 49L, 60L);
        when(resilientRedisService.executeScript(any(), anyList(), anyList(), anyString(), any()))
                .thenReturn(scriptResult);

        // When
        RateLimitResult result = algorithm.execute("192.168.1.1", testPolicy);

        // Then
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(49L);
    }

    @Test
    @DisplayName("Should deny request when sliding window exceeded")
    void execute_exceedsLimit_returnsDenied() {
        // Given
        List<Long> scriptResult = Arrays.asList(0L, 0L, 15L);
        when(resilientRedisService.executeScript(any(), anyList(), anyList(), anyString(), any()))
                .thenReturn(scriptResult);

        // When
        RateLimitResult result = algorithm.execute("192.168.1.1", testPolicy);

        // Then
        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfter()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Should handle null script result")
    void execute_nullResult_returnsDenied() {
        // Given
        when(resilientRedisService.executeScript(any(), anyList(), anyList(), anyString(), any()))
                .thenReturn(null);

        // When
        RateLimitResult result = algorithm.execute("192.168.1.1", testPolicy);

        // Then
        assertThat(result.allowed()).isFalse();
    }

    @Test
    @DisplayName("Should reset sliding log state")
    void reset_validKey_deletesKeys() {
        // When
        algorithm.reset("192.168.1.1");

        // Then
        verify(resilientRedisService).deleteKeys("rl:sliding:*:192.168.1.1");
    }
}
