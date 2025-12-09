package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.TenantRequest;
import com.example.ratelimiter.application.dto.TenantResponse;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.repository.TenantRepository;
import com.example.ratelimiter.infrastructure.config.CacheConfig;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;

    public List<TenantResponse> findAll() {
        return tenantRepository.findAll().stream()
                .map(TenantResponse::from)
                .toList();
    }

    @Cacheable(value = CacheConfig.TENANTS_CACHE, key = "#id", unless = "#result == null")
    public TenantResponse findById(UUID id) {
        return tenantRepository.findById(id)
                .map(TenantResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));
    }

    @Transactional
    @CachePut(value = CacheConfig.TENANTS_CACHE, key = "#result.id()")
    public TenantResponse create(TenantRequest request) {
        // Check if tenant with same name already exists
        if (tenantRepository.findByName(request.name()).isPresent()) {
            throw new DataIntegrityViolationException("Tenant with name '" + request.name() + "' already exists");
        }

        Tenant tenant = Tenant.builder()
                .name(request.name())
                .tier(request.tier())
                .enabled(request.enabled())
                .build();

        Tenant saved = tenantRepository.save(tenant);
        return TenantResponse.from(saved);
    }

    @Transactional
    @CachePut(value = CacheConfig.TENANTS_CACHE, key = "#id")
    public TenantResponse update(UUID id, TenantRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + id));

        // Check if updating name would violate uniqueness constraint
        if (!tenant.getName().equals(request.name())) {
            if (tenantRepository.findByName(request.name()).isPresent()) {
                throw new DataIntegrityViolationException("Tenant with name '" + request.name() + "' already exists");
            }
        }

        tenant.setName(request.name());
        tenant.setTier(request.tier());
        tenant.setEnabled(request.enabled());

        Tenant saved = tenantRepository.save(tenant);
        return TenantResponse.from(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.TENANTS_CACHE, key = "#id")
    public void delete(UUID id) {
        if (!tenantRepository.existsById(id)) {
            throw new EntityNotFoundException("Tenant not found with id: " + id);
        }
        tenantRepository.deleteById(id);
    }
}
