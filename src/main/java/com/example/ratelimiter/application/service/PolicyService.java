package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.PolicyRequest;
import com.example.ratelimiter.application.dto.PolicyResponse;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.TenantRepository;
import com.example.ratelimiter.infrastructure.config.CacheConfig;
import com.example.ratelimiter.infrastructure.resilience.FallbackHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final TenantRepository tenantRepository;
    private final FallbackHandler fallbackHandler;

    @CircuitBreaker(name = "database", fallbackMethod = "findAllFallback")
    @Retry(name = "database")
    public List<PolicyResponse> findAll() {
        return policyRepository.findAll().stream()
                .map(PolicyResponse::from)
                .toList();
    }

    @CircuitBreaker(name = "database", fallbackMethod = "findByIdFallback")
    @Retry(name = "database")
    @Cacheable(value = CacheConfig.POLICIES_CACHE, key = "#id", unless = "#result == null")
    public PolicyResponse findById(UUID id) {
        return policyRepository.findById(id)
                .map(PolicyResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + id));
    }

    @CircuitBreaker(name = "database", fallbackMethod = "findByTenantIdFallback")
    @Retry(name = "database")
    public List<PolicyResponse> findByTenantId(UUID tenantId) {
        return policyRepository.findByTenantId(tenantId).stream()
                .map(PolicyResponse::from)
                .toList();
    }

    @Transactional
    @Caching(
        put = {
            @CachePut(value = CacheConfig.POLICIES_CACHE, key = "#result.id()")
        },
        evict = {
            @CacheEvict(value = CacheConfig.POLICY_BY_NAME_CACHE, allEntries = true)
        }
    )
    public PolicyResponse create(PolicyRequest request) {
        Tenant tenant = null;
        if (request.tenantId() != null) {
            tenant = tenantRepository.findById(request.tenantId())
                    .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + request.tenantId()));
        }

        Policy policy = Policy.builder()
                .name(request.name())
                .description(request.description())
                .scope(request.scope())
                .algorithm(request.algorithm())
                .maxRequests(request.maxRequests())
                .windowSeconds(request.windowSeconds())
                .burstCapacity(request.burstCapacity())
                .refillRate(request.refillRate())
                .failMode(request.failMode())
                .enabled(request.enabled())
                .isDefault(request.isDefault())
                .tenant(tenant)
                .build();

        Policy saved = policyRepository.save(policy);
        return PolicyResponse.from(saved);
    }

    @Transactional
    @Caching(
        put = {
            @CachePut(value = CacheConfig.POLICIES_CACHE, key = "#id")
        },
        evict = {
            @CacheEvict(value = CacheConfig.POLICY_BY_NAME_CACHE, allEntries = true)
        }
    )
    public PolicyResponse update(UUID id, PolicyRequest request) {
        Policy policy = policyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + id));

        Tenant tenant = null;
        if (request.tenantId() != null) {
            tenant = tenantRepository.findById(request.tenantId())
                    .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + request.tenantId()));
        }

        policy.setName(request.name());
        policy.setDescription(request.description());
        policy.setScope(request.scope());
        policy.setAlgorithm(request.algorithm());
        policy.setMaxRequests(request.maxRequests());
        policy.setWindowSeconds(request.windowSeconds());
        policy.setBurstCapacity(request.burstCapacity());
        policy.setRefillRate(request.refillRate());
        policy.setFailMode(request.failMode());
        policy.setEnabled(request.enabled());
        policy.setDefault(request.isDefault());
        policy.setTenant(tenant);

        Policy saved = policyRepository.save(policy);
        return PolicyResponse.from(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.POLICIES_CACHE, key = "#id"),
        @CacheEvict(value = CacheConfig.POLICY_BY_NAME_CACHE, allEntries = true)
    })
    public void delete(UUID id) {
        if (!policyRepository.existsById(id)) {
            throw new EntityNotFoundException("Policy not found with id: " + id);
        }
        policyRepository.deleteById(id);
    }

    // ======================== Circuit Breaker Fallback Methods ========================

    /**
     * Fallback method for findAll when database circuit breaker is open.
     * Returns empty list to allow graceful degradation.
     */
    private List<PolicyResponse> findAllFallback(Throwable throwable) {
        log.error("Database circuit breaker triggered in findAll: {}", throwable.getMessage());
        return fallbackHandler.handleDatabaseListFailure(throwable);
    }

    /**
     * Fallback method for findById when database circuit breaker is open.
     * Returns null and logs the error. Calling code should handle null gracefully.
     */
    private PolicyResponse findByIdFallback(UUID id, Throwable throwable) {
        log.error("Database circuit breaker triggered in findById for id {}: {}",
                id, throwable.getMessage());
        return fallbackHandler.handleDatabaseGetFailure(id, throwable);
    }

    /**
     * Fallback method for findByTenantId when database circuit breaker is open.
     * Returns empty list to allow graceful degradation.
     */
    private List<PolicyResponse> findByTenantIdFallback(UUID tenantId, Throwable throwable) {
        log.error("Database circuit breaker triggered in findByTenantId for tenant {}: {}",
                tenantId, throwable.getMessage());
        return fallbackHandler.handleDatabaseByTenantFailure(tenantId, throwable);
    }
}
