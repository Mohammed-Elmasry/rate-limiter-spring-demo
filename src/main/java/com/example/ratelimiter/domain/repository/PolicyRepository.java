package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.enums.PolicyScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    List<Policy> findByEnabledTrue();

    List<Policy> findByTenantId(UUID tenantId);

    List<Policy> findByScope(PolicyScope scope);

    @Query("SELECT p FROM Policy p WHERE p.scope = :scope AND p.isDefault = true AND p.enabled = true")
    Optional<Policy> findDefaultByScope(@Param("scope") PolicyScope scope);

    @Query("SELECT p FROM Policy p WHERE p.tenant.id = :tenantId AND p.isDefault = true AND p.enabled = true")
    Optional<Policy> findDefaultByTenantId(@Param("tenantId") UUID tenantId);

    boolean existsByNameAndTenantId(String name, UUID tenantId);

    @Query("SELECT p FROM Policy p WHERE p.name = :name AND (p.tenant.id = :tenantId OR (p.tenant IS NULL AND :tenantId IS NULL))")
    Optional<Policy> findByNameAndTenantId(@Param("name") String name, @Param("tenantId") UUID tenantId);
}
