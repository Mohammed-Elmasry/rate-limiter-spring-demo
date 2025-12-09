package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.UserPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPolicyRepository extends JpaRepository<UserPolicy, UUID> {

    Optional<UserPolicy> findByUserIdAndTenantId(String userId, UUID tenantId);

    List<UserPolicy> findByUserId(String userId);

    List<UserPolicy> findByTenantId(UUID tenantId);

    List<UserPolicy> findByPolicyId(UUID policyId);

    List<UserPolicy> findByEnabledTrue();

    boolean existsByUserIdAndTenantId(String userId, UUID tenantId);
}
