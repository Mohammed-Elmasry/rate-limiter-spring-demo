package com.example.ratelimiter.domain.algorithm;

import com.example.ratelimiter.domain.enums.Algorithm;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating and managing rate limiting algorithm instances.
 *
 * This factory implements the Strategy pattern by maintaining a registry of
 * algorithm implementations and routing requests to the appropriate strategy
 * based on the algorithm type.
 *
 * Design considerations:
 * - Uses EnumMap for O(1) lookup performance and type safety
 * - Initializes at startup to fail fast if algorithms are missing
 * - Thread-safe: the map is immutable after initialization
 * - All algorithm implementations are Spring-managed singletons
 *
 * Benefits of this approach:
 * 1. Open/Closed Principle: Easy to add new algorithms without modifying existing code
 * 2. Dependency Inversion: High-level code depends on RateLimitAlgorithm abstraction
 * 3. Single Responsibility: Each algorithm class handles one algorithm
 * 4. Testability: Easy to mock individual algorithms in tests
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlgorithmFactory {

    private final List<RateLimitAlgorithm> algorithms;
    private Map<Algorithm, RateLimitAlgorithm> algorithmMap;

    /**
     * Initializes the algorithm registry after dependency injection.
     *
     * This method runs after Spring constructs the bean and injects all
     * RateLimitAlgorithm implementations. It builds a map for fast lookup
     * and validates that all enum values have implementations.
     *
     * @throws IllegalStateException if any Algorithm enum lacks an implementation
     */
    @PostConstruct
    public void init() {
        algorithmMap = new EnumMap<>(Algorithm.class);

        for (RateLimitAlgorithm algorithm : algorithms) {
            Algorithm type = algorithm.getType();
            if (algorithmMap.containsKey(type)) {
                log.warn("Duplicate algorithm implementation found for type: {}. Using: {}",
                        type, algorithm.getClass().getSimpleName());
            }
            algorithmMap.put(type, algorithm);
            log.info("Registered algorithm: {} -> {}",
                    type, algorithm.getClass().getSimpleName());
        }

        // Validate that all enum values have implementations
        for (Algorithm type : Algorithm.values()) {
            if (!algorithmMap.containsKey(type)) {
                throw new IllegalStateException(
                        "Missing implementation for algorithm: " + type);
            }
        }

        log.info("Algorithm factory initialized with {} algorithms", algorithmMap.size());
    }

    /**
     * Retrieves the appropriate algorithm implementation for the given type.
     *
     * This is the primary method used by clients of the factory. It provides
     * type-safe, fast access to algorithm implementations.
     *
     * @param type the algorithm type enum value
     * @return the corresponding RateLimitAlgorithm implementation
     * @throws IllegalArgumentException if type is null or no implementation exists
     */
    public RateLimitAlgorithm getAlgorithm(Algorithm type) {
        if (type == null) {
            throw new IllegalArgumentException("Algorithm type cannot be null");
        }

        RateLimitAlgorithm algorithm = algorithmMap.get(type);
        if (algorithm == null) {
            throw new IllegalArgumentException(
                    "No implementation found for algorithm: " + type);
        }

        return algorithm;
    }

    /**
     * Checks if an algorithm implementation exists for the given type.
     *
     * Useful for validation or conditional logic.
     *
     * @param type the algorithm type to check
     * @return true if an implementation exists, false otherwise
     */
    public boolean hasAlgorithm(Algorithm type) {
        return type != null && algorithmMap.containsKey(type);
    }
}
