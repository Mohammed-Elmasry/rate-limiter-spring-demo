package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for PolicyRepository using @DataJpaTest.
 *
 * @DataJpaTest provides:
 * - In-memory database (H2 by default)
 * - Transactional rollback after each test
 * - TestEntityManager for test data setup
 * - Only loads JPA components (no full Spring context)
 *
 * Tests focus on custom query methods and JPA interactions.
 */
@DataJpaTest
@DisplayName("PolicyRepository Tests")
class PolicyRepositoryTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Should find policies by enabled status")
    void findByEnabledTrue_policiesExist_returnEnabledOnly() {
        // Given
        Policy enabledPolicy = createPolicy("enabled-policy", true, false, PolicyScope.GLOBAL);
        Policy disabledPolicy = createPolicy("disabled-policy", false, false, PolicyScope.GLOBAL);

        entityManager.persistAndFlush(enabledPolicy);
        entityManager.persistAndFlush(disabledPolicy);

        // When
        List<Policy> enabledPolicies = policyRepository.findByEnabledTrue();

        // Then
        assertThat(enabledPolicies).hasSize(1);
        assertThat(enabledPolicies.get(0).getName()).isEqualTo("enabled-policy");
    }

    @Test
    @DisplayName("Should find policies by tenant ID")
    void findByTenantId_policiesExist_returnTenantPolicies() {
        // Given
        Tenant tenant1 = createTenant("tenant1");
        Tenant tenant2 = createTenant("tenant2");

        entityManager.persist(tenant1);
        entityManager.persist(tenant2);
        entityManager.flush();

        Policy policy1 = createPolicyWithTenant("policy1", tenant1);
        Policy policy2 = createPolicyWithTenant("policy2", tenant1);
        Policy policy3 = createPolicyWithTenant("policy3", tenant2);

        entityManager.persist(policy1);
        entityManager.persist(policy2);
        entityManager.persist(policy3);
        entityManager.flush();

        // When
        List<Policy> tenant1Policies = policyRepository.findByTenantId(tenant1.getId());

        // Then
        assertThat(tenant1Policies).hasSize(2);
        assertThat(tenant1Policies).allMatch(p -> p.getTenant().getId().equals(tenant1.getId()));
    }

    @Test
    @DisplayName("Should find policies by scope")
    void findByScope_policiesExist_returnScopedPolicies() {
        // Given
        Policy userPolicy = createPolicy("user-policy", true, false, PolicyScope.USER);
        Policy apiPolicy = createPolicy("api-policy", true, false, PolicyScope.API);
        Policy globalPolicy = createPolicy("global-policy", true, false, PolicyScope.GLOBAL);

        entityManager.persist(userPolicy);
        entityManager.persist(apiPolicy);
        entityManager.persist(globalPolicy);
        entityManager.flush();

        // When
        List<Policy> userPolicies = policyRepository.findByScope(PolicyScope.USER);

        // Then
        assertThat(userPolicies).hasSize(1);
        assertThat(userPolicies.get(0).getScope()).isEqualTo(PolicyScope.USER);
    }

    @Test
    @DisplayName("Should find default policy by scope")
    void findDefaultByScope_defaultExists_returnsDefaultPolicy() {
        // Given
        Policy defaultGlobal = createPolicy("default-global", true, true, PolicyScope.GLOBAL);
        Policy nonDefaultGlobal = createPolicy("non-default-global", true, false, PolicyScope.GLOBAL);

        entityManager.persist(defaultGlobal);
        entityManager.persist(nonDefaultGlobal);
        entityManager.flush();

        // When
        Optional<Policy> foundDefault = policyRepository.findDefaultByScope(PolicyScope.GLOBAL);

        // Then
        assertThat(foundDefault).isPresent();
        assertThat(foundDefault.get().getName()).isEqualTo("default-global");
        assertThat(foundDefault.get().isDefault()).isTrue();
    }

    @Test
    @DisplayName("Should find default policy by tenant ID")
    void findDefaultByTenantId_defaultExists_returnsDefaultPolicy() {
        // Given
        Tenant tenant = createTenant("test-tenant");
        entityManager.persist(tenant);
        entityManager.flush();

        Policy defaultTenantPolicy = createPolicyWithTenant("default-tenant-policy", tenant);
        defaultTenantPolicy.setDefault(true);

        Policy nonDefaultTenantPolicy = createPolicyWithTenant("non-default-tenant-policy", tenant);
        nonDefaultTenantPolicy.setDefault(false);

        entityManager.persist(defaultTenantPolicy);
        entityManager.persist(nonDefaultTenantPolicy);
        entityManager.flush();

        // When
        Optional<Policy> foundDefault = policyRepository.findDefaultByTenantId(tenant.getId());

        // Then
        assertThat(foundDefault).isPresent();
        assertThat(foundDefault.get().getName()).isEqualTo("default-tenant-policy");
        assertThat(foundDefault.get().isDefault()).isTrue();
    }

    @Test
    @DisplayName("Should return empty when no default policy exists")
    void findDefaultByScope_noDefault_returnsEmpty() {
        // Given
        Policy nonDefaultPolicy = createPolicy("non-default", true, false, PolicyScope.GLOBAL);
        entityManager.persist(nonDefaultPolicy);
        entityManager.flush();

        // When
        Optional<Policy> foundDefault = policyRepository.findDefaultByScope(PolicyScope.GLOBAL);

        // Then
        assertThat(foundDefault).isEmpty();
    }

    @Test
    @DisplayName("Should find policy by name and tenant ID")
    void findByNameAndTenantId_policyExists_returnsPolicy() {
        // Given
        Tenant tenant = createTenant("test-tenant");
        entityManager.persist(tenant);
        entityManager.flush();

        Policy policy = createPolicyWithTenant("unique-name", tenant);
        entityManager.persist(policy);
        entityManager.flush();

        // When
        Optional<Policy> found = policyRepository.findByNameAndTenantId("unique-name", tenant.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("unique-name");
        assertThat(found.get().getTenant().getId()).isEqualTo(tenant.getId());
    }

    @Test
    @DisplayName("Should check if policy exists by name and tenant ID")
    void existsByNameAndTenantId_policyExists_returnsTrue() {
        // Given
        Tenant tenant = createTenant("test-tenant");
        entityManager.persist(tenant);
        entityManager.flush();

        Policy policy = createPolicyWithTenant("unique-name", tenant);
        entityManager.persist(policy);
        entityManager.flush();

        // When
        boolean exists = policyRepository.existsByNameAndTenantId("unique-name", tenant.getId());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should return false when policy does not exist by name and tenant ID")
    void existsByNameAndTenantId_policyNotExists_returnsFalse() {
        // Given
        Tenant tenant = createTenant("test-tenant");
        entityManager.persist(tenant);
        entityManager.flush();

        // When
        boolean exists = policyRepository.existsByNameAndTenantId("non-existent", tenant.getId());

        // Then
        assertThat(exists).isFalse();
    }

    // Helper methods
    private Policy createPolicy(String name, boolean enabled, boolean isDefault, PolicyScope scope) {
        return Policy.builder()
                .name(name)
                .description("Test policy")
                .scope(scope)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(enabled)
                .isDefault(isDefault)
                .build();
    }

    private Policy createPolicyWithTenant(String name, Tenant tenant) {
        Policy policy = createPolicy(name, true, false, PolicyScope.TENANT);
        policy.setTenant(tenant);
        return policy;
    }

    private Tenant createTenant(String name) {
        return Tenant.builder()
                .name(name)
                .enabled(true)
                .build();
    }
}
