package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.RateLimitEvent;
import com.example.ratelimiter.domain.enums.IdentifierType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RateLimitEventRepository extends JpaRepository<RateLimitEvent, Long> {

    Page<RateLimitEvent> findByPolicyId(UUID policyId, Pageable pageable);

    Page<RateLimitEvent> findByIdentifier(String identifier, Pageable pageable);

    Page<RateLimitEvent> findByIdentifierType(IdentifierType identifierType, Pageable pageable);

    @Query("SELECT e FROM RateLimitEvent e WHERE e.eventTime >= :startTime AND e.eventTime < :endTime")
    List<RateLimitEvent> findByEventTimeBetween(
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    @Query("SELECT e FROM RateLimitEvent e WHERE e.policyId = :policyId AND e.eventTime >= :startTime AND e.eventTime < :endTime")
    List<RateLimitEvent> findByPolicyIdAndEventTimeBetween(
        @Param("policyId") UUID policyId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    @Query("SELECT e FROM RateLimitEvent e WHERE e.identifier = :identifier AND e.eventTime >= :startTime AND e.eventTime < :endTime")
    List<RateLimitEvent> findByIdentifierAndEventTimeBetween(
        @Param("identifier") String identifier,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    @Query("SELECT COUNT(e) FROM RateLimitEvent e WHERE e.policyId = :policyId AND e.allowed = false AND e.eventTime >= :startTime")
    long countRejectedEventsByPolicyIdSince(
        @Param("policyId") UUID policyId,
        @Param("startTime") OffsetDateTime startTime
    );

    @Query("SELECT COUNT(e) FROM RateLimitEvent e WHERE e.identifier = :identifier AND e.allowed = false AND e.eventTime >= :startTime")
    long countRejectedEventsByIdentifierSince(
        @Param("identifier") String identifier,
        @Param("startTime") OffsetDateTime startTime
    );

    // Metrics aggregation queries
    @Query("SELECT COUNT(e) FROM RateLimitEvent e WHERE e.policyId = :policyId AND e.eventTime >= :startTime AND e.eventTime < :endTime")
    long countByPolicyIdAndTimeBetween(
        @Param("policyId") UUID policyId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    @Query("SELECT COUNT(e) FROM RateLimitEvent e WHERE e.policyId = :policyId AND e.allowed = true AND e.eventTime >= :startTime AND e.eventTime < :endTime")
    long countAllowedByPolicyIdAndTimeBetween(
        @Param("policyId") UUID policyId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    @Query("SELECT COUNT(e) FROM RateLimitEvent e WHERE e.policyId = :policyId AND e.allowed = false AND e.eventTime >= :startTime AND e.eventTime < :endTime")
    long countDeniedByPolicyIdAndTimeBetween(
        @Param("policyId") UUID policyId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    @Query("SELECT e.identifierType, COUNT(e) FROM RateLimitEvent e WHERE e.policyId = :policyId GROUP BY e.identifierType")
    List<Object[]> countByPolicyIdGroupedByIdentifierType(@Param("policyId") UUID policyId);

    @Query("SELECT COUNT(e) FROM RateLimitEvent e WHERE e.policyId = :policyId")
    long countByPolicyId(@Param("policyId") UUID policyId);

    @Query("SELECT COUNT(e) FROM RateLimitEvent e WHERE e.policyId = :policyId AND e.allowed = true")
    long countAllowedByPolicyId(@Param("policyId") UUID policyId);

    @Query("SELECT COUNT(e) FROM RateLimitEvent e WHERE e.policyId = :policyId AND e.allowed = false")
    long countDeniedByPolicyId(@Param("policyId") UUID policyId);
}
