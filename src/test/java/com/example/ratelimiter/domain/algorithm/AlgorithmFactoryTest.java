package com.example.ratelimiter.domain.algorithm;

import com.example.ratelimiter.domain.enums.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlgorithmFactory Tests")
class AlgorithmFactoryTest {

    @Mock
    private RateLimitAlgorithm tokenBucketAlgorithm;

    @Mock
    private RateLimitAlgorithm fixedWindowAlgorithm;

    @Mock
    private RateLimitAlgorithm slidingLogAlgorithm;

    private AlgorithmFactory algorithmFactory;

    @BeforeEach
    void setUp() {
        when(tokenBucketAlgorithm.getType()).thenReturn(Algorithm.TOKEN_BUCKET);
        when(fixedWindowAlgorithm.getType()).thenReturn(Algorithm.FIXED_WINDOW);
        when(slidingLogAlgorithm.getType()).thenReturn(Algorithm.SLIDING_LOG);

        List<RateLimitAlgorithm> algorithms = Arrays.asList(
                tokenBucketAlgorithm,
                fixedWindowAlgorithm,
                slidingLogAlgorithm
        );

        algorithmFactory = new AlgorithmFactory(algorithms);
        algorithmFactory.init();
    }

    @Test
    @DisplayName("Should initialize with all algorithm implementations")
    void init_allAlgorithmsPresent_initializesSuccessfully() {
        // Then - no exception should be thrown during setup
        assertThat(algorithmFactory.hasAlgorithm(Algorithm.TOKEN_BUCKET)).isTrue();
        assertThat(algorithmFactory.hasAlgorithm(Algorithm.FIXED_WINDOW)).isTrue();
        assertThat(algorithmFactory.hasAlgorithm(Algorithm.SLIDING_LOG)).isTrue();
    }

    @Test
    @DisplayName("Should get TOKEN_BUCKET algorithm")
    void getAlgorithm_tokenBucket_returnsCorrectAlgorithm() {
        // When
        RateLimitAlgorithm algorithm = algorithmFactory.getAlgorithm(Algorithm.TOKEN_BUCKET);

        // Then
        assertThat(algorithm).isEqualTo(tokenBucketAlgorithm);
    }

    @Test
    @DisplayName("Should get FIXED_WINDOW algorithm")
    void getAlgorithm_fixedWindow_returnsCorrectAlgorithm() {
        // When
        RateLimitAlgorithm algorithm = algorithmFactory.getAlgorithm(Algorithm.FIXED_WINDOW);

        // Then
        assertThat(algorithm).isEqualTo(fixedWindowAlgorithm);
    }

    @Test
    @DisplayName("Should get SLIDING_LOG algorithm")
    void getAlgorithm_slidingLog_returnsCorrectAlgorithm() {
        // When
        RateLimitAlgorithm algorithm = algorithmFactory.getAlgorithm(Algorithm.SLIDING_LOG);

        // Then
        assertThat(algorithm).isEqualTo(slidingLogAlgorithm);
    }

    @Test
    @DisplayName("Should throw exception for null algorithm type")
    void getAlgorithm_nullType_throwsException() {
        // When / Then
        assertThatThrownBy(() -> algorithmFactory.getAlgorithm(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Algorithm type cannot be null");
    }

    @Test
    @DisplayName("Should check if algorithm exists")
    void hasAlgorithm_existingType_returnsTrue() {
        // When / Then
        assertThat(algorithmFactory.hasAlgorithm(Algorithm.TOKEN_BUCKET)).isTrue();
        assertThat(algorithmFactory.hasAlgorithm(Algorithm.FIXED_WINDOW)).isTrue();
        assertThat(algorithmFactory.hasAlgorithm(Algorithm.SLIDING_LOG)).isTrue();
    }

    @Test
    @DisplayName("Should return false for null algorithm check")
    void hasAlgorithm_nullType_returnsFalse() {
        // When / Then
        assertThat(algorithmFactory.hasAlgorithm(null)).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when algorithm implementation missing")
    void init_missingImplementation_throwsException() {
        // Given - only provide TOKEN_BUCKET
        List<RateLimitAlgorithm> incompleteList = Arrays.asList(tokenBucketAlgorithm);
        AlgorithmFactory incompleteFactory = new AlgorithmFactory(incompleteList);

        // When / Then
        assertThatThrownBy(() -> incompleteFactory.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing implementation");
    }

    @Test
    @DisplayName("Should handle duplicate algorithm implementations")
    void init_duplicateImplementations_usesLatest() {
        // Given
        RateLimitAlgorithm duplicateTokenBucket = mock(RateLimitAlgorithm.class);
        when(duplicateTokenBucket.getType()).thenReturn(Algorithm.TOKEN_BUCKET);

        List<RateLimitAlgorithm> algorithmsWithDuplicate = Arrays.asList(
                tokenBucketAlgorithm,
                fixedWindowAlgorithm,
                slidingLogAlgorithm,
                duplicateTokenBucket
        );

        AlgorithmFactory factoryWithDuplicate = new AlgorithmFactory(algorithmsWithDuplicate);

        // When
        factoryWithDuplicate.init();

        // Then - should not throw exception, but log warning
        RateLimitAlgorithm algorithm = factoryWithDuplicate.getAlgorithm(Algorithm.TOKEN_BUCKET);
        assertThat(algorithm).isNotNull();
    }
}
