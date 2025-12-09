package com.example.ratelimiter.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine for high-performance in-memory caching.
 *
 * This configuration provides separate cache instances for different data types
 * with optimized TTL and size limits based on usage patterns:
 *
 * - policies: Cache for policy entities (moderate TTL, medium size)
 * - policyByName: Cache for policy lookups by name (same as policies)
 * - tenants: Cache for tenant entities (longer TTL, smaller size)
 * - ipRules: Cache for IP rule lookups (shorter TTL, larger size due to frequent lookups)
 * - apiKeys: Cache for API key validations (moderate TTL, medium size)
 * - ipPolicies: Cache for IP-to-policy mappings (shorter TTL, larger size)
 *
 * Cache Strategy:
 * - Write-through: Updates/creates use @CachePut to keep cache synchronized
 * - Cache-aside: Reads use @Cacheable to populate cache on miss
 * - Eviction: Deletes use @CacheEvict to maintain consistency
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String POLICIES_CACHE = "policies";
    public static final String POLICY_BY_NAME_CACHE = "policyByName";
    public static final String TENANTS_CACHE = "tenants";
    public static final String IP_RULES_CACHE = "ipRules";
    public static final String API_KEYS_CACHE = "apiKeys";
    public static final String IP_POLICIES_CACHE = "ipPolicies";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            POLICIES_CACHE,
            POLICY_BY_NAME_CACHE,
            TENANTS_CACHE,
            IP_RULES_CACHE,
            API_KEYS_CACHE,
            IP_POLICIES_CACHE
        );

        // Allow dynamic cache creation for flexibility
        cacheManager.setAllowNullValues(false);

        // Use custom Caffeine configuration per cache
        cacheManager.registerCustomCache(POLICIES_CACHE, buildPoliciesCache());
        cacheManager.registerCustomCache(POLICY_BY_NAME_CACHE, buildPolicyByNameCache());
        cacheManager.registerCustomCache(TENANTS_CACHE, buildTenantsCache());
        cacheManager.registerCustomCache(IP_RULES_CACHE, buildIpRulesCache());
        cacheManager.registerCustomCache(API_KEYS_CACHE, buildApiKeysCache());
        cacheManager.registerCustomCache(IP_POLICIES_CACHE, buildIpPoliciesCache());

        return cacheManager;
    }

    /**
     * Policies cache: moderate TTL since policies change occasionally but not frequently
     */
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildPoliciesCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats() // Enable statistics for monitoring
                .build();
    }

    /**
     * Policy by name cache: same configuration as policies cache
     * Separate cache to allow independent eviction strategies
     */
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildPolicyByNameCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * Tenants cache: longer TTL since tenants rarely change
     * Smaller size as tenant count is typically limited
     */
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildTenantsCache() {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * IP rules cache: shorter TTL for security - IP rules may need quick updates
     * Larger size as IP lookups are frequent in rate limiting
     */
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildIpRulesCache() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * API keys cache: moderate TTL with access-based expiry
     * Expire after access to automatically remove unused keys
     */
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildApiKeysCache() {
        return Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * IP policies cache: caches the policy resolution for specific IPs
     * Short TTL for security and flexibility
     */
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildIpPoliciesCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * Alternative configuration using CaffeineSpec for externalized configuration.
     * Can be used with application.yml/properties for dynamic tuning.
     */
    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats();
    }
}
