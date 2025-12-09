/**
 * Rate limiting algorithm implementations using the Strategy design pattern.
 *
 * <h2>Overview</h2>
 * This package contains the core abstraction and implementations for rate limiting algorithms.
 * Each algorithm is implemented as a separate strategy, making it easy to add new algorithms
 * or modify existing ones without affecting the rest of the codebase.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.example.ratelimiter.domain.algorithm.RateLimitAlgorithm} - Strategy interface</li>
 *   <li>{@link com.example.ratelimiter.domain.algorithm.RateLimitResult} - Value object for results</li>
 *   <li>{@link com.example.ratelimiter.domain.algorithm.AlgorithmFactory} - Factory for algorithm selection</li>
 *   <li>{@link com.example.ratelimiter.domain.algorithm.TokenBucketAlgorithm} - Token bucket implementation</li>
 *   <li>{@link com.example.ratelimiter.domain.algorithm.FixedWindowAlgorithm} - Fixed window implementation</li>
 *   <li>{@link com.example.ratelimiter.domain.algorithm.SlidingLogAlgorithm} - Sliding log implementation</li>
 * </ul>
 *
 * <h2>Algorithm Comparison</h2>
 * <table border="1">
 *   <tr>
 *     <th>Algorithm</th>
 *     <th>Accuracy</th>
 *     <th>Memory</th>
 *     <th>Burst Handling</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>Token Bucket</td>
 *     <td>High</td>
 *     <td>Low</td>
 *     <td>Excellent</td>
 *     <td>APIs needing burst capacity</td>
 *   </tr>
 *   <tr>
 *     <td>Fixed Window</td>
 *     <td>Medium</td>
 *     <td>Very Low</td>
 *     <td>Poor (boundary issue)</td>
 *     <td>Simple use cases, low memory</td>
 *   </tr>
 *   <tr>
 *     <td>Sliding Log</td>
 *     <td>Perfect</td>
 *     <td>High</td>
 *     <td>Good</td>
 *     <td>Strict rate limiting required</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     private final AlgorithmFactory algorithmFactory;
 *
 *     public void checkLimit(String userId, Policy policy) {
 *         RateLimitAlgorithm algorithm = algorithmFactory.getAlgorithm(policy.getAlgorithm());
 *         RateLimitResult result = algorithm.execute(userId, policy);
 *
 *         if (!result.allowed()) {
 *             throw new RateLimitException("Rate limit exceeded");
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * All classes in this package are designed to be thread-safe:
 * <ul>
 *   <li>Algorithm implementations are stateless Spring beans</li>
 *   <li>AlgorithmFactory is immutable after initialization</li>
 *   <li>RateLimitResult is an immutable record</li>
 * </ul>
 *
 * <h2>Adding New Algorithms</h2>
 * To add a new algorithm:
 * <ol>
 *   <li>Create a Lua script in {@code src/main/resources/redis/scripts/}</li>
 *   <li>Configure the script as a Spring bean in Redis configuration</li>
 *   <li>Create a class implementing {@link com.example.ratelimiter.domain.algorithm.RateLimitAlgorithm}</li>
 *   <li>Annotate with {@code @Component} for auto-registration</li>
 *   <li>Add the algorithm type to {@link com.example.ratelimiter.domain.enums.Algorithm} enum</li>
 * </ol>
 *
 * @since 1.1.0
 * @see com.example.ratelimiter.domain.algorithm.RateLimitAlgorithm
 * @see com.example.ratelimiter.domain.algorithm.AlgorithmFactory
 */
package com.example.ratelimiter.domain.algorithm;
