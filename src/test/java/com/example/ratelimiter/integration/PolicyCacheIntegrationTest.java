package com.example.ratelimiter.integration;

import com.example.ratelimiter.application.dto.PolicyRequest;
import com.example.ratelimiter.application.dto.PolicyResponse;
import com.example.ratelimiter.application.service.PolicyService;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for cache behavior.
 *
 * Tests caching annotations (@Cacheable, @CachePut, @CacheEvict) on PolicyService:
 * 1. @Cacheable methods cache their results
 * 2. @CachePut updates cache on writes
 * 3. @CacheEvict clears cache on deletes
 * 4. Cache entries are properly keyed and isolated
 *
 * Uses @SpyBean to verify repository interactions are minimized by caching.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.cache.type=caffeine",
        "spring.cache.caffeine.spec=maximumSize=100,expireAfterWrite=10m"
})
@DisplayName("Policy Cache Integration Tests")
class PolicyCacheIntegrationTest {

    @Autowired
    private PolicyService policyService;

    @SpyBean
    private PolicyRepository policyRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("Should cache policy on findById")
    void findById_firstCall_cachesResult() {
        // Given
        PolicyRequest request = new PolicyRequest(
                "cache-test-policy",
                "Test caching",
                PolicyScope.GLOBAL,
                Algorithm.TOKEN_BUCKET,
                100,
                60,
                null,
                null,
                FailMode.FAIL_CLOSED,
                true,
                false,
                null
        );

        PolicyResponse created = policyService.create(request);
        UUID policyId = created.id();

        // Clear any cache from create
        clearPolicyCache(policyId);
        reset(policyRepository);

        // When - First call should hit database
        PolicyResponse firstCall = policyService.findById(policyId);
        // Second call should hit cache
        PolicyResponse secondCall = policyService.findById(policyId);

        // Then
        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();
        assertThat(firstCall.id()).isEqualTo(secondCall.id());

        // Repository should only be called once (first call)
        verify(policyRepository, times(1)).findById(policyId);
    }

    @Test
    @DisplayName("Should update cache on policy update")
    void update_existingPolicy_updatesCache() {
        // Given
        PolicyRequest createRequest = new PolicyRequest(
                "cache-update-test",
                "Test update",
                PolicyScope.GLOBAL,
                Algorithm.FIXED_WINDOW,
                100,
                60,
                null,
                null,
                FailMode.FAIL_CLOSED,
                true,
                false,
                null
        );

        PolicyResponse created = policyService.create(createRequest);
        UUID policyId = created.id();

        // Populate cache
        policyService.findById(policyId);

        // When - Update policy
        PolicyRequest updateRequest = new PolicyRequest(
                "cache-update-test-modified",
                "Test update modified",
                PolicyScope.GLOBAL,
                Algorithm.SLIDING_LOG,
                200,
                120,
                null,
                null,
                FailMode.FAIL_OPEN,
                true,
                false,
                null
        );

        PolicyResponse updated = policyService.update(policyId, updateRequest);

        // Clear spy interactions
        reset(policyRepository);

        // Then - Next findById should return updated value from cache
        PolicyResponse fromCache = policyService.findById(policyId);

        assertThat(fromCache.name()).isEqualTo("cache-update-test-modified");
        assertThat(fromCache.maxRequests()).isEqualTo(200);
        assertThat(fromCache.algorithm()).isEqualTo(Algorithm.SLIDING_LOG);

        // Repository should not be called (served from cache)
        verify(policyRepository, never()).findById(policyId);
    }

    @Test
    @DisplayName("Should evict cache on policy delete")
    void delete_existingPolicy_evictsCache() {
        // Given
        PolicyRequest request = new PolicyRequest(
                "cache-delete-test",
                "Test delete",
                PolicyScope.GLOBAL,
                Algorithm.TOKEN_BUCKET,
                100,
                60,
                null,
                null,
                FailMode.FAIL_CLOSED,
                true,
                false,
                null
        );

        PolicyResponse created = policyService.create(request);
        UUID policyId = created.id();

        // Populate cache
        policyService.findById(policyId);

        // When - Delete policy
        policyService.delete(policyId);

        // Then - Cache should be evicted
        var cache = cacheManager.getCache("policies");
        assertThat(cache).isNotNull();
        assertThat(cache.get(policyId)).isNull();
    }

    @Test
    @DisplayName("Should isolate cache entries by key")
    void findById_multipleEntries_isolatesByKey() {
        // Given - Create two policies
        PolicyRequest request1 = new PolicyRequest(
                "cache-isolation-1",
                "Test 1",
                PolicyScope.GLOBAL,
                Algorithm.TOKEN_BUCKET,
                100,
                60,
                null,
                null,
                FailMode.FAIL_CLOSED,
                true,
                false,
                null
        );

        PolicyRequest request2 = new PolicyRequest(
                "cache-isolation-2",
                "Test 2",
                PolicyScope.GLOBAL,
                Algorithm.FIXED_WINDOW,
                200,
                120,
                null,
                null,
                FailMode.FAIL_OPEN,
                true,
                false,
                null
        );

        PolicyResponse policy1 = policyService.create(request1);
        PolicyResponse policy2 = policyService.create(request2);

        clearPolicyCache(policy1.id());
        clearPolicyCache(policy2.id());
        reset(policyRepository);

        // When - Access both policies
        PolicyResponse cached1 = policyService.findById(policy1.id());
        PolicyResponse cached2 = policyService.findById(policy2.id());

        // Access again (should hit cache)
        PolicyResponse cachedAgain1 = policyService.findById(policy1.id());
        PolicyResponse cachedAgain2 = policyService.findById(policy2.id());

        // Then
        assertThat(cached1.name()).isEqualTo("cache-isolation-1");
        assertThat(cached2.name()).isEqualTo("cache-isolation-2");

        // Repository should be called once per unique policy
        verify(policyRepository, times(1)).findById(policy1.id());
        verify(policyRepository, times(1)).findById(policy2.id());
    }

    @Test
    @DisplayName("Should handle null values correctly (not cache)")
    void findById_nonExistentPolicy_doesNotCacheNull() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When / Then - Should throw exception (not cache null)
        try {
            policyService.findById(nonExistentId);
        } catch (Exception e) {
            // Expected
        }

        // Verify cache does not contain null entry
        var cache = cacheManager.getCache("policies");
        assertThat(cache).isNotNull();
        assertThat(cache.get(nonExistentId)).isNull();
    }

    // Helper methods
    private void clearPolicyCache(UUID policyId) {
        var cache = cacheManager.getCache("policies");
        if (cache != null) {
            cache.evict(policyId);
        }
    }
}
