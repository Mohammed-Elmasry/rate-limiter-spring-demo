package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    List<ApiKey> findByTenantId(UUID tenantId);

    List<ApiKey> findByEnabledTrue();

    List<ApiKey> findByTenantIdAndEnabledTrue(UUID tenantId);

    @Query("SELECT a FROM ApiKey a WHERE a.enabled = true AND a.keyHash = :keyHash AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    Optional<ApiKey> findActiveByKeyHash(@Param("keyHash") String keyHash, @Param("now") OffsetDateTime now);

    @Query("SELECT a FROM ApiKey a WHERE a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    List<ApiKey> findExpiredKeys(@Param("now") OffsetDateTime now);

    boolean existsByKeyHash(String keyHash);

    boolean existsByKeyPrefix(String keyPrefix);
}
