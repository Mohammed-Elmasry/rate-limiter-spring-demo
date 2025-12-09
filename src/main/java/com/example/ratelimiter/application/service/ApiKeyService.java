package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.ApiKeyCreatedResponse;
import com.example.ratelimiter.application.dto.ApiKeyRequest;
import com.example.ratelimiter.application.dto.ApiKeyResponse;
import com.example.ratelimiter.domain.entity.ApiKey;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.repository.ApiKeyRepository;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.TenantRepository;
import com.example.ratelimiter.infrastructure.config.CacheConfig;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final PolicyRepository policyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public List<ApiKeyResponse> findAll() {
        return apiKeyRepository.findAll().stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Cacheable(value = CacheConfig.API_KEYS_CACHE, key = "#id", unless = "#result == null")
    public ApiKeyResponse findById(UUID id) {
        return apiKeyRepository.findById(id)
                .map(ApiKeyResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("API key not found with id: " + id));
    }

    @Transactional
    public ApiKeyCreatedResponse create(ApiKeyRequest request) {
        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + request.tenantId()));

        Policy policy = null;
        if (request.policyId() != null) {
            policy = policyRepository.findById(request.policyId())
                    .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));
        }

        // Generate API key: rl_live_ prefix + 32 random bytes encoded as base64url
        String rawKey = generateApiKey();
        String keyHash = hashApiKey(rawKey);
        String keyPrefix = rawKey.substring(0, Math.min(12, rawKey.length()));

        ApiKey apiKey = ApiKey.builder()
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .name(request.name())
                .tenant(tenant)
                .policy(policy)
                .enabled(request.enabled())
                .expiresAt(request.expiresAt())
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);

        return ApiKeyCreatedResponse.builder()
                .id(saved.getId())
                .apiKey(rawKey)
                .keyPrefix(saved.getKeyPrefix())
                .name(saved.getName())
                .tenantId(saved.getTenant().getId())
                .tenantName(saved.getTenant().getName())
                .policyId(saved.getPolicy() != null ? saved.getPolicy().getId() : null)
                .policyName(saved.getPolicy() != null ? saved.getPolicy().getName() : null)
                .enabled(saved.isEnabled())
                .expiresAt(saved.getExpiresAt())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    @CachePut(value = CacheConfig.API_KEYS_CACHE, key = "#id")
    public ApiKeyResponse update(UUID id, ApiKeyRequest request) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("API key not found with id: " + id));

        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + request.tenantId()));

        Policy policy = null;
        if (request.policyId() != null) {
            policy = policyRepository.findById(request.policyId())
                    .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));
        }

        apiKey.setName(request.name());
        apiKey.setTenant(tenant);
        apiKey.setPolicy(policy);
        apiKey.setEnabled(request.enabled());
        apiKey.setExpiresAt(request.expiresAt());

        ApiKey saved = apiKeyRepository.save(apiKey);
        return ApiKeyResponse.from(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.API_KEYS_CACHE, key = "#id")
    public void delete(UUID id) {
        if (!apiKeyRepository.existsById(id)) {
            throw new EntityNotFoundException("API key not found with id: " + id);
        }
        apiKeyRepository.deleteById(id);
    }

    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return "rl_live_" + encoded;
    }

    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
