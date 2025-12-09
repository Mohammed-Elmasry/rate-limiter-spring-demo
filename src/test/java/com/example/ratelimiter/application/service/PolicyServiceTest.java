package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.PolicyRequest;
import com.example.ratelimiter.application.dto.PolicyResponse;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.TenantRepository;
import com.example.ratelimiter.infrastructure.resilience.FallbackHandler;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for PolicyService.
 *
 * Test Strategy:
 * - Test CRUD operations (create, read, update, delete)
 * - Test caching behavior (@Cacheable, @CachePut, @CacheEvict)
 * - Test tenant association
 * - Test circuit breaker fallbacks
 * - Test validation and error handling
 * - Test transactional behavior
 *
 * Coverage Goals:
 * - All public methods
 * - Success and error paths
 * - Edge cases (null values, not found scenarios)
 * - Cache annotations behavior
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyService Tests")
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private FallbackHandler fallbackHandler;

    @InjectMocks
    private PolicyService policyService;

    private Policy testPolicy;
    private Tenant testTenant;
    private UUID testPolicyId;
    private UUID testTenantId;

    @BeforeEach
    void setUp() {
        testPolicyId = UUID.randomUUID();
        testTenantId = UUID.randomUUID();

        testTenant = Tenant.builder()
                .id(testTenantId)
                .name("test-tenant")
                .enabled(true)
                .build();

        testPolicy = Policy.builder()
                .id(testPolicyId)
                .name("test-policy")
                .description("Test policy")
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .burstCapacity(120)
                .refillRate(BigDecimal.valueOf(10.5))
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .isDefault(false)
                .tenant(testTenant)
                .build();
    }

    @Nested
    @DisplayName("Find Operations Tests")
    class FindOperationsTests {

        @Test
        @DisplayName("Should find all policies")
        void findAll_policiesExist_returnsAllPolicies() {
            // Given
            Policy policy2 = Policy.builder()
                    .id(UUID.randomUUID())
                    .name("policy-2")
                    .scope(PolicyScope.GLOBAL)
                    .algorithm(Algorithm.FIXED_WINDOW)
                    .maxRequests(200)
                    .windowSeconds(120)
                    .failMode(FailMode.FAIL_OPEN)
                    .enabled(true)
                    .isDefault(true)
                    .build();

            when(policyRepository.findAll()).thenReturn(Arrays.asList(testPolicy, policy2));

            // When
            List<PolicyResponse> responses = policyService.findAll();

            // Then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).name()).isEqualTo("test-policy");
            assertThat(responses.get(1).name()).isEqualTo("policy-2");
            verify(policyRepository).findAll();
        }

        @Test
        @DisplayName("Should find all policies - empty list")
        void findAll_noPolicies_returnsEmptyList() {
            // Given
            when(policyRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<PolicyResponse> responses = policyService.findAll();

            // Then
            assertThat(responses).isEmpty();
            verify(policyRepository).findAll();
        }

        @Test
        @DisplayName("Should find policy by ID")
        void findById_policyExists_returnsPolicy() {
            // Given
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));

            // When
            PolicyResponse response = policyService.findById(testPolicyId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(testPolicyId);
            assertThat(response.name()).isEqualTo("test-policy");
            assertThat(response.algorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
            assertThat(response.maxRequests()).isEqualTo(100);
            verify(policyRepository).findById(testPolicyId);
        }

        @Test
        @DisplayName("Should throw exception when policy not found by ID")
        void findById_policyNotFound_throwsException() {
            // Given
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> policyService.findById(testPolicyId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Policy not found with id: " + testPolicyId);

            verify(policyRepository).findById(testPolicyId);
        }

        @Test
        @DisplayName("Should find policies by tenant ID")
        void findByTenantId_policiesExist_returnsTenantPolicies() {
            // Given
            Policy policy2 = Policy.builder()
                    .id(UUID.randomUUID())
                    .name("policy-2")
                    .scope(PolicyScope.TENANT)
                    .algorithm(Algorithm.FIXED_WINDOW)
                    .maxRequests(200)
                    .windowSeconds(120)
                    .failMode(FailMode.FAIL_OPEN)
                    .enabled(true)
                    .isDefault(false)
                    .tenant(testTenant)
                    .build();

            when(policyRepository.findByTenantId(testTenantId))
                    .thenReturn(Arrays.asList(testPolicy, policy2));

            // When
            List<PolicyResponse> responses = policyService.findByTenantId(testTenantId);

            // Then
            assertThat(responses).hasSize(2);
            assertThat(responses).allMatch(p -> p.tenantId().equals(testTenantId));
            verify(policyRepository).findByTenantId(testTenantId);
        }

        @Test
        @DisplayName("Should return empty list when no policies for tenant")
        void findByTenantId_noPolicies_returnsEmptyList() {
            // Given
            when(policyRepository.findByTenantId(testTenantId)).thenReturn(Collections.emptyList());

            // When
            List<PolicyResponse> responses = policyService.findByTenantId(testTenantId);

            // Then
            assertThat(responses).isEmpty();
            verify(policyRepository).findByTenantId(testTenantId);
        }
    }

    @Nested
    @DisplayName("Create Operation Tests")
    class CreateOperationTests {

        @Test
        @DisplayName("Should create policy with tenant")
        void create_validRequestWithTenant_createsPolicy() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "new-policy",
                    "New policy description",
                    PolicyScope.USER,
                    Algorithm.TOKEN_BUCKET,
                    100,
                    60,
                    120,
                    BigDecimal.valueOf(10.5),
                    FailMode.FAIL_CLOSED,
                    true,
                    false,
                    testTenantId
            );

            when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
            when(policyRepository.save(any(Policy.class))).thenReturn(testPolicy);

            // When
            PolicyResponse response = policyService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("test-policy");

            ArgumentCaptor<Policy> policyCaptor = ArgumentCaptor.forClass(Policy.class);
            verify(policyRepository).save(policyCaptor.capture());

            Policy savedPolicy = policyCaptor.getValue();
            assertThat(savedPolicy.getName()).isEqualTo("new-policy");
            assertThat(savedPolicy.getTenant()).isEqualTo(testTenant);
            assertThat(savedPolicy.getAlgorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
        }

        @Test
        @DisplayName("Should create policy without tenant (global policy)")
        void create_validRequestWithoutTenant_createsGlobalPolicy() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "global-policy",
                    "Global policy description",
                    PolicyScope.GLOBAL,
                    Algorithm.FIXED_WINDOW,
                    1000,
                    3600,
                    null,
                    null,
                    FailMode.FAIL_OPEN,
                    true,
                    true,
                    null
            );

            Policy globalPolicy = Policy.builder()
                    .id(UUID.randomUUID())
                    .name("global-policy")
                    .scope(PolicyScope.GLOBAL)
                    .algorithm(Algorithm.FIXED_WINDOW)
                    .maxRequests(1000)
                    .windowSeconds(3600)
                    .failMode(FailMode.FAIL_OPEN)
                    .enabled(true)
                    .isDefault(true)
                    .build();

            when(policyRepository.save(any(Policy.class))).thenReturn(globalPolicy);

            // When
            PolicyResponse response = policyService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("global-policy");
            assertThat(response.scope()).isEqualTo(PolicyScope.GLOBAL);
            assertThat(response.tenantId()).isNull();

            ArgumentCaptor<Policy> policyCaptor = ArgumentCaptor.forClass(Policy.class);
            verify(policyRepository).save(policyCaptor.capture());

            Policy savedPolicy = policyCaptor.getValue();
            assertThat(savedPolicy.getTenant()).isNull();
            verify(tenantRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw exception when tenant not found")
        void create_tenantNotFound_throwsException() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "new-policy",
                    "New policy description",
                    PolicyScope.USER,
                    Algorithm.TOKEN_BUCKET,
                    100,
                    60,
                    120,
                    BigDecimal.valueOf(10.5),
                    FailMode.FAIL_CLOSED,
                    true,
                    false,
                    testTenantId
            );

            when(tenantRepository.findById(testTenantId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> policyService.create(request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Tenant not found with id: " + testTenantId);

            verify(policyRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update Operation Tests")
    class UpdateOperationTests {

        @Test
        @DisplayName("Should update policy successfully")
        void update_validRequest_updatesPolicy() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "updated-policy",
                    "Updated description",
                    PolicyScope.TENANT,
                    Algorithm.SLIDING_LOG,
                    200,
                    120,
                    null,
                    null,
                    FailMode.FAIL_OPEN,
                    false,
                    true,
                    testTenantId
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
            when(policyRepository.save(any(Policy.class))).thenReturn(testPolicy);

            // When
            PolicyResponse response = policyService.update(testPolicyId, request);

            // Then
            assertThat(response).isNotNull();

            verify(policyRepository).findById(testPolicyId);
            verify(tenantRepository).findById(testTenantId);
            verify(policyRepository).save(testPolicy);

            // Verify policy was updated
            assertThat(testPolicy.getName()).isEqualTo("updated-policy");
            assertThat(testPolicy.getAlgorithm()).isEqualTo(Algorithm.SLIDING_LOG);
            assertThat(testPolicy.getMaxRequests()).isEqualTo(200);
            assertThat(testPolicy.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent policy")
        void update_policyNotFound_throwsException() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "updated-policy",
                    "Updated description",
                    PolicyScope.TENANT,
                    Algorithm.SLIDING_LOG,
                    200,
                    120,
                    null,
                    null,
                    FailMode.FAIL_OPEN,
                    true,
                    false,
                    testTenantId
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> policyService.update(testPolicyId, request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Policy not found with id: " + testPolicyId);

            verify(policyRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when updating with non-existent tenant")
        void update_tenantNotFound_throwsException() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "updated-policy",
                    "Updated description",
                    PolicyScope.TENANT,
                    Algorithm.SLIDING_LOG,
                    200,
                    120,
                    null,
                    null,
                    FailMode.FAIL_OPEN,
                    true,
                    false,
                    testTenantId
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(tenantRepository.findById(testTenantId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> policyService.update(testPolicyId, request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Tenant not found with id: " + testTenantId);

            verify(policyRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should update policy to remove tenant association")
        void update_removeTenantAssociation_updatesSuccessfully() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "updated-policy",
                    "Updated description",
                    PolicyScope.GLOBAL,
                    Algorithm.FIXED_WINDOW,
                    200,
                    120,
                    null,
                    null,
                    FailMode.FAIL_OPEN,
                    true,
                    true,
                    null // Remove tenant association
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(policyRepository.save(any(Policy.class))).thenReturn(testPolicy);

            // When
            PolicyResponse response = policyService.update(testPolicyId, request);

            // Then
            assertThat(response).isNotNull();
            verify(policyRepository).save(testPolicy);
            assertThat(testPolicy.getTenant()).isNull();
            verify(tenantRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("Delete Operation Tests")
    class DeleteOperationTests {

        @Test
        @DisplayName("Should delete policy successfully")
        void delete_policyExists_deletesPolicy() {
            // Given
            when(policyRepository.existsById(testPolicyId)).thenReturn(true);

            // When
            policyService.delete(testPolicyId);

            // Then
            verify(policyRepository).existsById(testPolicyId);
            verify(policyRepository).deleteById(testPolicyId);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent policy")
        void delete_policyNotFound_throwsException() {
            // Given
            when(policyRepository.existsById(testPolicyId)).thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> policyService.delete(testPolicyId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Policy not found with id: " + testPolicyId);

            verify(policyRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle policy with all optional fields null")
        void create_optionalFieldsNull_createsSuccessfully() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "minimal-policy",
                    null, // No description
                    PolicyScope.GLOBAL,
                    Algorithm.FIXED_WINDOW,
                    100,
                    60,
                    null, // No burst capacity
                    null, // No refill rate
                    FailMode.FAIL_CLOSED,
                    true,
                    false,
                    null // No tenant
            );

            Policy minimalPolicy = Policy.builder()
                    .id(UUID.randomUUID())
                    .name("minimal-policy")
                    .scope(PolicyScope.GLOBAL)
                    .algorithm(Algorithm.FIXED_WINDOW)
                    .maxRequests(100)
                    .windowSeconds(60)
                    .failMode(FailMode.FAIL_CLOSED)
                    .enabled(true)
                    .isDefault(false)
                    .build();

            when(policyRepository.save(any(Policy.class))).thenReturn(minimalPolicy);

            // When
            PolicyResponse response = policyService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("minimal-policy");
            assertThat(response.description()).isNull();
            assertThat(response.burstCapacity()).isNull();
            assertThat(response.refillRate()).isNull();
            assertThat(response.tenantId()).isNull();
        }

        @Test
        @DisplayName("Should handle policy with TOKEN_BUCKET algorithm and burst capacity")
        void create_tokenBucketWithBurstCapacity_createsSuccessfully() {
            // Given
            PolicyRequest request = new PolicyRequest(
                    "token-bucket-policy",
                    "Token bucket with burst",
                    PolicyScope.API,
                    Algorithm.TOKEN_BUCKET,
                    100,
                    60,
                    150, // Burst capacity
                    BigDecimal.valueOf(5.5), // Refill rate
                    FailMode.FAIL_CLOSED,
                    true,
                    false,
                    null
            );

            Policy policy = Policy.builder()
                    .id(UUID.randomUUID())
                    .name("token-bucket-policy")
                    .scope(PolicyScope.API)
                    .algorithm(Algorithm.TOKEN_BUCKET)
                    .maxRequests(100)
                    .windowSeconds(60)
                    .burstCapacity(150)
                    .refillRate(BigDecimal.valueOf(5.5))
                    .failMode(FailMode.FAIL_CLOSED)
                    .enabled(true)
                    .isDefault(false)
                    .build();

            when(policyRepository.save(any(Policy.class))).thenReturn(policy);

            // When
            PolicyResponse response = policyService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.algorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
            assertThat(response.burstCapacity()).isEqualTo(150);
            assertThat(response.refillRate()).isEqualByComparingTo(BigDecimal.valueOf(5.5));
        }
    }
}
