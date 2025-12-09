package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.UserPolicyRequest;
import com.example.ratelimiter.application.dto.UserPolicyResponse;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.entity.UserPolicy;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.TenantRepository;
import com.example.ratelimiter.domain.repository.UserPolicyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPolicyService {

    private final UserPolicyRepository userPolicyRepository;
    private final PolicyRepository policyRepository;
    private final TenantRepository tenantRepository;

    public List<UserPolicyResponse> findAll() {
        return userPolicyRepository.findAll().stream()
                .map(UserPolicyResponse::from)
                .toList();
    }

    public UserPolicyResponse findById(UUID id) {
        return userPolicyRepository.findById(id)
                .map(UserPolicyResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("User policy not found with id: " + id));
    }

    public UserPolicyResponse findByUserId(String userId, UUID tenantId) {
        return userPolicyRepository.findByUserIdAndTenantId(userId, tenantId)
                .map(UserPolicyResponse::from)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User policy not found for user: " + userId + " in tenant: " + tenantId));
    }

    @Transactional
    public UserPolicyResponse create(UserPolicyRequest request) {
        // Check if user policy already exists
        if (userPolicyRepository.existsByUserIdAndTenantId(request.userId(), request.tenantId())) {
            throw new DataIntegrityViolationException(
                    "User policy already exists for user: " + request.userId() + " in tenant: " + request.tenantId());
        }

        Policy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));

        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + request.tenantId()));

        UserPolicy userPolicy = UserPolicy.builder()
                .userId(request.userId())
                .policy(policy)
                .tenant(tenant)
                .enabled(request.enabled())
                .build();

        UserPolicy saved = userPolicyRepository.save(userPolicy);
        return UserPolicyResponse.from(saved);
    }

    @Transactional
    public UserPolicyResponse update(UUID id, UserPolicyRequest request) {
        UserPolicy userPolicy = userPolicyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User policy not found with id: " + id));

        // Check if updating would violate uniqueness constraint
        if (!userPolicy.getUserId().equals(request.userId()) ||
            !userPolicy.getTenant().getId().equals(request.tenantId())) {
            if (userPolicyRepository.existsByUserIdAndTenantId(request.userId(), request.tenantId())) {
                throw new DataIntegrityViolationException(
                        "User policy already exists for user: " + request.userId() + " in tenant: " + request.tenantId());
            }
        }

        Policy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));

        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + request.tenantId()));

        userPolicy.setUserId(request.userId());
        userPolicy.setPolicy(policy);
        userPolicy.setTenant(tenant);
        userPolicy.setEnabled(request.enabled());

        UserPolicy saved = userPolicyRepository.save(userPolicy);
        return UserPolicyResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!userPolicyRepository.existsById(id)) {
            throw new EntityNotFoundException("User policy not found with id: " + id);
        }
        userPolicyRepository.deleteById(id);
    }
}
