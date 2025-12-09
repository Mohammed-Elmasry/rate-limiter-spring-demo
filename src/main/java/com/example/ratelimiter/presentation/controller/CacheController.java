package com.example.ratelimiter.presentation.controller;

import com.example.ratelimiter.infrastructure.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for cache management and monitoring.
 *
 * Provides endpoints for:
 * - Viewing cache statistics
 * - Clearing individual caches or all caches
 * - Getting detailed cache information
 *
 * These endpoints are useful for:
 * - Operational monitoring and troubleshooting
 * - Cache invalidation during deployments
 * - Performance tuning and analysis
 */
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@Slf4j
public class CacheController {

    private final CacheManager cacheManager;
    private final CacheMetrics cacheMetrics;

    /**
     * Get all cache names.
     *
     * @return list of cache names
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCaches() {
        Map<String, Object> response = new HashMap<>();
        response.put("caches", cacheManager.getCacheNames());
        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed statistics for a specific cache.
     *
     * @param cacheName the name of the cache
     * @return cache statistics
     */
    @GetMapping("/{cacheName}/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats(@PathVariable String cacheName) {
        String stats = cacheMetrics.getCacheStatistics(cacheName);

        if (stats == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("cacheName", cacheName);
        response.put("statistics", stats);

        return ResponseEntity.ok(response);
    }

    /**
     * Clear a specific cache.
     *
     * @param cacheName the name of the cache to clear
     * @return success message
     */
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<Map<String, String>> clearCache(@PathVariable String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);

        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        cache.clear();
        log.info("Cache '{}' cleared via REST API", cacheName);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Cache '" + cacheName + "' cleared successfully");
        response.put("cacheName", cacheName);

        return ResponseEntity.ok(response);
    }

    /**
     * Clear all caches.
     *
     * WARNING: This operation will impact application performance temporarily
     * as all cached data will need to be reloaded from the database.
     *
     * @return success message
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> clearAllCaches() {
        cacheMetrics.clearAllCaches();

        Map<String, String> response = new HashMap<>();
        response.put("message", "All caches cleared successfully");
        response.put("warning", "Application performance may be temporarily impacted");

        return ResponseEntity.ok(response);
    }

    /**
     * Get statistics for all caches.
     *
     * @return map of cache name to statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, String>> getAllCacheStats() {
        Map<String, String> stats = new HashMap<>();

        cacheManager.getCacheNames().forEach(cacheName -> {
            String cacheStats = cacheMetrics.getCacheStatistics(cacheName);
            if (cacheStats != null) {
                stats.put(cacheName, cacheStats);
            }
        });

        return ResponseEntity.ok(stats);
    }
}
