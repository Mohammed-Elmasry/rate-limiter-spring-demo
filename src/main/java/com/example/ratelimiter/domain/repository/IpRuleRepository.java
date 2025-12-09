package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.IpRule;
import com.example.ratelimiter.domain.enums.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IpRuleRepository extends JpaRepository<IpRule, UUID> {

    List<IpRule> findByEnabledTrue();

    List<IpRule> findByTenantId(UUID tenantId);

    List<IpRule> findByRuleType(RuleType ruleType);

    @Query(value = """
        SELECT * FROM ip_rules
        WHERE enabled = true
        AND rule_type = 'RATE_LIMIT'
        AND (
            (ip_address IS NOT NULL AND ip_address = CAST(:ip AS INET))
            OR
            (ip_cidr IS NOT NULL AND ip_cidr >>= CAST(:ip AS INET))
        )
        ORDER BY
            CASE WHEN ip_address IS NOT NULL THEN 1 ELSE 2 END,
            created_at DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<IpRule> findMatchingRuleLimitRuleForIp(@Param("ip") String ip);

    @Query(value = """
        SELECT * FROM ip_rules
        WHERE enabled = true
        AND rule_type = 'RATE_LIMIT'
        AND tenant_id = :tenantId
        AND (
            (ip_address IS NOT NULL AND ip_address = CAST(:ip AS INET))
            OR
            (ip_cidr IS NOT NULL AND ip_cidr >>= CAST(:ip AS INET))
        )
        ORDER BY
            CASE WHEN ip_address IS NOT NULL THEN 1 ELSE 2 END,
            created_at DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<IpRule> findMatchingRateLimitRuleForIpAndTenant(
        @Param("ip") String ip,
        @Param("tenantId") UUID tenantId
    );

    @Query(value = """
        SELECT EXISTS(
            SELECT 1 FROM ip_rules
            WHERE enabled = true
            AND (
                (ip_address IS NOT NULL AND ip_address = CAST(:ip AS INET))
                OR
                (ip_cidr IS NOT NULL AND ip_cidr >>= CAST(:ip AS INET))
            )
        )
        """, nativeQuery = true)
    boolean existsMatchingRuleForIp(@Param("ip") String ip);
}
