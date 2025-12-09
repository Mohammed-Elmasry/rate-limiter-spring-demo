package com.example.ratelimiter.infrastructure.metrics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Exposes Caffeine cache metrics to Micrometer/Prometheus for monitoring.
 *
 * This component:
 * - Registers cache statistics gauges for hit rate, miss rate, eviction count, load count, etc.
 * - Periodically logs cache statistics for operational visibility
 * - Enables monitoring of cache effectiveness via Prometheus/Grafana
 *
 * Metrics exposed:
 * - cache.size: Current number of entries in the cache
 * - cache.requests: Total number of cache requests (hits + misses)
 * - cache.hits: Number of successful cache lookups
 * - cache.misses: Number of cache lookups that missed
 * - cache.hit.ratio: Percentage of requests that were cache hits
 * - cache.evictions: Number of cache evictions
 * - cache.load.success: Number of successful cache loads
 * - cache.load.failure: Number of failed cache loads
 * - cache.load.time: Average time to load cache entries (in nanoseconds)
 */
@Slf4j
@Component
public class CacheMetrics implements MeterBinder {

    private final CacheManager cacheManager;
    private MeterRegistry meterRegistry;

    public CacheMetrics(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.meterRegistry = registry;

        // Register gauges for each cache
        cacheManager.getCacheNames().forEach(cacheName -> {
            org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
            if (springCache instanceof CaffeineCache caffeineCache) {
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();

                List<Tag> tags = List.of(Tag.of("cache", cacheName));

                // Cache size
                registry.gauge("cache.size", tags, nativeCache, cache -> cache.estimatedSize());

                // Register cache stats if available
                CacheStats stats = nativeCache.stats();

                // Request count (hits + misses)
                registry.gauge("cache.requests", tags, stats,
                    s -> s.hitCount() + s.missCount());

                // Hit count
                registry.gauge("cache.hits", tags, stats, CacheStats::hitCount);

                // Miss count
                registry.gauge("cache.misses", tags, stats, CacheStats::missCount);

                // Hit ratio (percentage)
                registry.gauge("cache.hit.ratio", tags, stats, CacheStats::hitRate);

                // Eviction count
                registry.gauge("cache.evictions", tags, stats, CacheStats::evictionCount);

                // Load success count
                registry.gauge("cache.load.success", tags, stats, CacheStats::loadSuccessCount);

                // Load failure count
                registry.gauge("cache.load.failure", tags, stats, CacheStats::loadFailureCount);

                // Average load time (nanoseconds)
                registry.gauge("cache.load.time.avg", tags, stats, CacheStats::averageLoadPenalty);

                log.info("Registered cache metrics for cache: {}", cacheName);
            }
        });
    }

    /**
     * Periodically log cache statistics for operational visibility.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
    public void logCacheStatistics() {
        if (log.isInfoEnabled()) {
            log.info("=== Cache Statistics ===");

            cacheManager.getCacheNames().forEach(cacheName -> {
                org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
                if (springCache instanceof CaffeineCache caffeineCache) {
                    Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                    CacheStats stats = nativeCache.stats();

                    long size = nativeCache.estimatedSize();
                    long requests = stats.requestCount();
                    long hits = stats.hitCount();
                    long misses = stats.missCount();
                    double hitRate = stats.hitRate() * 100;
                    long evictions = stats.evictionCount();

                    log.info("Cache: {} | Size: {} | Requests: {} | Hits: {} | Misses: {} | Hit Rate: {:.2f}% | Evictions: {}",
                        cacheName, size, requests, hits, misses, hitRate, evictions);
                }
            });

            log.info("========================");
        }
    }

    /**
     * Manually clear all caches. Useful for testing or emergency cache invalidation.
     * This operation is logged for audit purposes.
     */
    public void clearAllCaches() {
        log.warn("Clearing all caches - this operation affects application performance");

        cacheManager.getCacheNames().forEach(cacheName -> {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cleared cache: {}", cacheName);
            }
        });

        log.info("All caches cleared successfully");
    }

    /**
     * Get detailed statistics for a specific cache.
     *
     * @param cacheName the name of the cache
     * @return formatted statistics string or null if cache not found
     */
    public String getCacheStatistics(String cacheName) {
        org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);

        if (springCache instanceof CaffeineCache caffeineCache) {
            Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            CacheStats stats = nativeCache.stats();

            return String.format(
                "Cache: %s\n" +
                "  Size: %d\n" +
                "  Requests: %d (Hits: %d, Misses: %d)\n" +
                "  Hit Rate: %.2f%%\n" +
                "  Evictions: %d\n" +
                "  Load Success: %d, Load Failure: %d\n" +
                "  Average Load Time: %.2f ms",
                cacheName,
                nativeCache.estimatedSize(),
                stats.requestCount(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate() * 100,
                stats.evictionCount(),
                stats.loadSuccessCount(),
                stats.loadFailureCount(),
                stats.averageLoadPenalty() / 1_000_000.0 // Convert nanoseconds to milliseconds
            );
        }

        return null;
    }
}
