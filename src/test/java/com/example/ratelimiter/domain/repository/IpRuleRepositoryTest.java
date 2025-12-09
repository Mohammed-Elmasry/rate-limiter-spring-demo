package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.IpRule;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.enums.RuleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for IpRuleRepository.
 *
 * Note: IP matching tests require PostgreSQL-specific functions (INET, CIDR).
 * These tests may need to be run with PostgreSQL test container or be marked
 * as integration tests. For unit tests, we test basic CRUD operations.
 */
@DataJpaTest
@DisplayName("IpRuleRepository Tests")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class IpRuleRepositoryTest {

    @Autowired
    private IpRuleRepository ipRuleRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Should find enabled IP rules")
    void findByEnabledTrue_rulesExist_returnsEnabledOnly() {
        // Given
        Policy policy = createAndSavePolicy();
        IpRule enabledRule = createIpRule("192.168.1.1", RuleType.RATE_LIMIT, policy, true);
        IpRule disabledRule = createIpRule("192.168.1.2", RuleType.RATE_LIMIT, policy, false);

        entityManager.persist(enabledRule);
        entityManager.persist(disabledRule);
        entityManager.flush();

        // When
        List<IpRule> enabledRules = ipRuleRepository.findByEnabledTrue();

        // Then
        assertThat(enabledRules).hasSize(1);
        assertThat(enabledRules.get(0).getIpAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("Should find IP rules by tenant ID")
    void findByTenantId_rulesExist_returnsTenantRules() {
        // Given
        Tenant tenant1 = createAndSaveTenant("tenant1");
        Tenant tenant2 = createAndSaveTenant("tenant2");
        Policy policy = createAndSavePolicy();

        IpRule rule1 = createIpRuleWithTenant("192.168.1.1", policy, tenant1);
        IpRule rule2 = createIpRuleWithTenant("192.168.1.2", policy, tenant1);
        IpRule rule3 = createIpRuleWithTenant("192.168.1.3", policy, tenant2);

        entityManager.persist(rule1);
        entityManager.persist(rule2);
        entityManager.persist(rule3);
        entityManager.flush();

        // When
        List<IpRule> tenant1Rules = ipRuleRepository.findByTenantId(tenant1.getId());

        // Then
        assertThat(tenant1Rules).hasSize(2);
        assertThat(tenant1Rules).allMatch(r -> r.getTenant().getId().equals(tenant1.getId()));
    }

    @Test
    @DisplayName("Should find IP rules by rule type")
    void findByRuleType_rulesExist_returnsMatchingType() {
        // Given
        Policy policy = createAndSavePolicy();
        IpRule rateLimitRule = createIpRule("192.168.1.1", RuleType.RATE_LIMIT, policy, true);
        IpRule blacklistRule = createIpRule("192.168.1.2", RuleType.BLACKLIST, policy, true);
        IpRule whitelistRule = createIpRule("192.168.1.3", RuleType.WHITELIST, policy, true);

        entityManager.persist(rateLimitRule);
        entityManager.persist(blacklistRule);
        entityManager.persist(whitelistRule);
        entityManager.flush();

        // When
        List<IpRule> rateLimitRules = ipRuleRepository.findByRuleType(RuleType.RATE_LIMIT);
        List<IpRule> blacklistRules = ipRuleRepository.findByRuleType(RuleType.BLACKLIST);

        // Then
        assertThat(rateLimitRules).hasSize(1);
        assertThat(rateLimitRules.get(0).getRuleType()).isEqualTo(RuleType.RATE_LIMIT);
        assertThat(blacklistRules).hasSize(1);
        assertThat(blacklistRules.get(0).getRuleType()).isEqualTo(RuleType.BLACKLIST);
    }

    // Note: IP matching tests (findMatchingRuleLimitRuleForIp) require PostgreSQL
    // They should be tested in integration tests with actual PostgreSQL database

    // Helper methods
    private Policy createAndSavePolicy() {
        Policy policy = Policy.builder()
                .name("test-policy")
                .scope(PolicyScope.GLOBAL)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .build();
        return entityManager.persist(policy);
    }

    private Tenant createAndSaveTenant(String name) {
        Tenant tenant = Tenant.builder()
                .name(name)
                .enabled(true)
                .build();
        return entityManager.persist(tenant);
    }

    private IpRule createIpRule(String ipAddress, RuleType ruleType, Policy policy, boolean enabled) {
        return IpRule.builder()
                .ipAddress(ipAddress)
                .ruleType(ruleType)
                .policy(policy)
                .description("Test rule")
                .enabled(enabled)
                .build();
    }

    private IpRule createIpRuleWithTenant(String ipAddress, Policy policy, Tenant tenant) {
        IpRule rule = createIpRule(ipAddress, RuleType.RATE_LIMIT, policy, true);
        rule.setTenant(tenant);
        return rule;
    }
}
