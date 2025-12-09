package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.IpRuleRequest;
import com.example.ratelimiter.application.dto.IpRuleResponse;
import com.example.ratelimiter.domain.entity.IpRule;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.enums.RuleType;
import com.example.ratelimiter.domain.repository.IpRuleRepository;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.TenantRepository;
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
 * Comprehensive unit tests for IpRuleService.
 *
 * Test Strategy:
 * - Test CRUD operations
 * - Test IP matching logic (exact IP and CIDR)
 * - Test policy resolution for IPs
 * - Test caching behavior
 * - Test tenant isolation
 * - Test rule type filtering (RATE_LIMIT, BLACKLIST, WHITELIST)
 *
 * Coverage Goals:
 * - All public methods
 * - IP matching edge cases
 * - Cache eviction scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IpRuleService Tests")
class IpRuleServiceTest {

    @Mock
    private IpRuleRepository ipRuleRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private IpRuleService ipRuleService;

    private IpRule testIpRule;
    private Policy testPolicy;
    private Tenant testTenant;
    private UUID testIpRuleId;
    private UUID testPolicyId;
    private UUID testTenantId;

    @BeforeEach
    void setUp() {
        testIpRuleId = UUID.randomUUID();
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
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .build();

        testIpRule = IpRule.builder()
                .id(testIpRuleId)
                .ipAddress("192.168.1.1")
                .ruleType(RuleType.RATE_LIMIT)
                .policy(testPolicy)
                .tenant(testTenant)
                .description("Test IP rule")
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("Find Operations Tests")
    class FindOperationsTests {

        @Test
        @DisplayName("Should find all IP rules")
        void findAll_rulesExist_returnsAllRules() {
            // Given
            IpRule rule2 = IpRule.builder()
                    .id(UUID.randomUUID())
                    .ipAddress("10.0.0.1")
                    .ruleType(RuleType.BLACKLIST)
                    .policy(testPolicy)
                    .enabled(true)
                    .build();

            when(ipRuleRepository.findAll()).thenReturn(Arrays.asList(testIpRule, rule2));

            // When
            List<IpRuleResponse> responses = ipRuleService.findAll();

            // Then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).ipAddress()).isEqualTo("192.168.1.1");
            assertThat(responses.get(1).ipAddress()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("Should find IP rule by ID")
        void findById_ruleExists_returnsRule() {
            // Given
            when(ipRuleRepository.findById(testIpRuleId)).thenReturn(Optional.of(testIpRule));

            // When
            IpRuleResponse response = ipRuleService.findById(testIpRuleId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(testIpRuleId);
            assertThat(response.ipAddress()).isEqualTo("192.168.1.1");
            assertThat(response.ruleType()).isEqualTo(RuleType.RATE_LIMIT);
        }

        @Test
        @DisplayName("Should throw exception when IP rule not found")
        void findById_ruleNotFound_throwsException() {
            // Given
            when(ipRuleRepository.findById(testIpRuleId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> ipRuleService.findById(testIpRuleId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("IP rule not found with id: " + testIpRuleId);
        }

        @Test
        @DisplayName("Should find IP rules by tenant ID")
        void findByTenantId_rulesExist_returnsTenantRules() {
            // Given
            when(ipRuleRepository.findByTenantId(testTenantId))
                    .thenReturn(Collections.singletonList(testIpRule));

            // When
            List<IpRuleResponse> responses = ipRuleService.findByTenantId(testTenantId);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).tenantId()).isEqualTo(testTenantId);
        }

        @Test
        @DisplayName("Should find IP rules by rule type")
        void findByRuleType_rulesExist_returnsFilteredRules() {
            // Given
            when(ipRuleRepository.findByRuleType(RuleType.RATE_LIMIT))
                    .thenReturn(Collections.singletonList(testIpRule));

            // When
            List<IpRuleResponse> responses = ipRuleService.findByRuleType("RATE_LIMIT");

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).ruleType()).isEqualTo(RuleType.RATE_LIMIT);
        }
    }

    @Nested
    @DisplayName("IP Matching Tests")
    class IpMatchingTests {

        @Test
        @DisplayName("Should find matching rate limit rule for IP")
        void findMatchingRateLimitRuleForIp_matchExists_returnsRule() {
            // Given
            String ipAddress = "192.168.1.1";
            when(ipRuleRepository.findMatchingRuleLimitRuleForIp(ipAddress))
                    .thenReturn(Optional.of(testIpRule));

            // When
            Optional<IpRuleResponse> response = ipRuleService.findMatchingRateLimitRuleForIp(ipAddress);

            // Then
            assertThat(response).isPresent();
            assertThat(response.get().ipAddress()).isEqualTo(ipAddress);
        }

        @Test
        @DisplayName("Should return empty when no matching IP rule")
        void findMatchingRateLimitRuleForIp_noMatch_returnsEmpty() {
            // Given
            String ipAddress = "1.2.3.4";
            when(ipRuleRepository.findMatchingRuleLimitRuleForIp(ipAddress))
                    .thenReturn(Optional.empty());

            // When
            Optional<IpRuleResponse> response = ipRuleService.findMatchingRateLimitRuleForIp(ipAddress);

            // Then
            assertThat(response).isEmpty();
        }

        @Test
        @DisplayName("Should find matching rate limit rule for IP and tenant")
        void findMatchingRateLimitRuleForIpAndTenant_matchExists_returnsRule() {
            // Given
            String ipAddress = "192.168.1.1";
            when(ipRuleRepository.findMatchingRateLimitRuleForIpAndTenant(ipAddress, testTenantId))
                    .thenReturn(Optional.of(testIpRule));

            // When
            Optional<IpRuleResponse> response = ipRuleService.findMatchingRateLimitRuleForIpAndTenant(
                    ipAddress, testTenantId);

            // Then
            assertThat(response).isPresent();
            assertThat(response.get().ipAddress()).isEqualTo(ipAddress);
            assertThat(response.get().tenantId()).isEqualTo(testTenantId);
        }

        @Test
        @DisplayName("Should get policy for IP address")
        void getPolicyForIp_matchExists_returnsPolicy() {
            // Given
            String ipAddress = "192.168.1.1";
            when(ipRuleRepository.findMatchingRuleLimitRuleForIp(ipAddress))
                    .thenReturn(Optional.of(testIpRule));

            // When
            Optional<Policy> policy = ipRuleService.getPolicyForIp(ipAddress, null);

            // Then
            assertThat(policy).isPresent();
            assertThat(policy.get().getId()).isEqualTo(testPolicyId);
        }

        @Test
        @DisplayName("Should get policy for IP address with tenant")
        void getPolicyForIp_withTenant_returnsPolicy() {
            // Given
            String ipAddress = "192.168.1.1";
            when(ipRuleRepository.findMatchingRateLimitRuleForIpAndTenant(ipAddress, testTenantId))
                    .thenReturn(Optional.of(testIpRule));

            // When
            Optional<Policy> policy = ipRuleService.getPolicyForIp(ipAddress, testTenantId);

            // Then
            assertThat(policy).isPresent();
            assertThat(policy.get().getId()).isEqualTo(testPolicyId);
            verify(ipRuleRepository).findMatchingRateLimitRuleForIpAndTenant(ipAddress, testTenantId);
            verify(ipRuleRepository, never()).findMatchingRuleLimitRuleForIp(anyString());
        }
    }

    @Nested
    @DisplayName("Create Operation Tests")
    class CreateOperationTests {

        @Test
        @DisplayName("Should create IP rule with exact IP address")
        void create_exactIpAddress_createsRule() {
            // Given
            IpRuleRequest request = new IpRuleRequest(
                    "192.168.1.100",
                    null,
                    RuleType.RATE_LIMIT,
                    testPolicyId,
                    testTenantId,
                    "Test rule",
                    true
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
            when(ipRuleRepository.save(any(IpRule.class))).thenReturn(testIpRule);

            // When
            IpRuleResponse response = ipRuleService.create(request);

            // Then
            assertThat(response).isNotNull();

            ArgumentCaptor<IpRule> captor = ArgumentCaptor.forClass(IpRule.class);
            verify(ipRuleRepository).save(captor.capture());

            IpRule savedRule = captor.getValue();
            assertThat(savedRule.getIpAddress()).isEqualTo("192.168.1.100");
            assertThat(savedRule.getIpCidr()).isNull();
            assertThat(savedRule.getRuleType()).isEqualTo(RuleType.RATE_LIMIT);
        }

        @Test
        @DisplayName("Should create IP rule with CIDR notation")
        void create_cidrNotation_createsRule() {
            // Given
            IpRuleRequest request = new IpRuleRequest(
                    null,
                    "192.168.1.0/24",
                    RuleType.RATE_LIMIT,
                    testPolicyId,
                    testTenantId,
                    "CIDR rule",
                    true
            );

            IpRule cidrRule = IpRule.builder()
                    .id(UUID.randomUUID())
                    .ipCidr("192.168.1.0/24")
                    .ruleType(RuleType.RATE_LIMIT)
                    .policy(testPolicy)
                    .tenant(testTenant)
                    .enabled(true)
                    .build();

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
            when(ipRuleRepository.save(any(IpRule.class))).thenReturn(cidrRule);

            // When
            IpRuleResponse response = ipRuleService.create(request);

            // Then
            assertThat(response).isNotNull();

            ArgumentCaptor<IpRule> captor = ArgumentCaptor.forClass(IpRule.class);
            verify(ipRuleRepository).save(captor.capture());

            IpRule savedRule = captor.getValue();
            assertThat(savedRule.getIpCidr()).isEqualTo("192.168.1.0/24");
            assertThat(savedRule.getIpAddress()).isNull();
        }

        @Test
        @DisplayName("Should create IP rule without tenant (global)")
        void create_withoutTenant_createsGlobalRule() {
            // Given
            IpRuleRequest request = new IpRuleRequest(
                    "192.168.1.100",
                    null,
                    RuleType.WHITELIST,
                    testPolicyId,
                    null,
                    "Global whitelist",
                    true
            );

            IpRule globalRule = IpRule.builder()
                    .id(UUID.randomUUID())
                    .ipAddress("192.168.1.100")
                    .ruleType(RuleType.WHITELIST)
                    .policy(testPolicy)
                    .enabled(true)
                    .build();

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(ipRuleRepository.save(any(IpRule.class))).thenReturn(globalRule);

            // When
            IpRuleResponse response = ipRuleService.create(request);

            // Then
            assertThat(response).isNotNull();
            verify(tenantRepository, never()).findById(any());

            ArgumentCaptor<IpRule> captor = ArgumentCaptor.forClass(IpRule.class);
            verify(ipRuleRepository).save(captor.capture());

            IpRule savedRule = captor.getValue();
            assertThat(savedRule.getTenant()).isNull();
        }

        @Test
        @DisplayName("Should throw exception when policy not found")
        void create_policyNotFound_throwsException() {
            // Given
            IpRuleRequest request = new IpRuleRequest(
                    "192.168.1.100",
                    null,
                    RuleType.RATE_LIMIT,
                    testPolicyId,
                    testTenantId,
                    "Test rule",
                    true
            );

            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> ipRuleService.create(request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Policy not found with id: " + testPolicyId);

            verify(ipRuleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update Operation Tests")
    class UpdateOperationTests {

        @Test
        @DisplayName("Should update IP rule successfully")
        void update_validRequest_updatesRule() {
            // Given
            IpRuleRequest request = new IpRuleRequest(
                    "10.0.0.1",
                    null,
                    RuleType.BLACKLIST,
                    testPolicyId,
                    testTenantId,
                    "Updated rule",
                    false
            );

            when(ipRuleRepository.findById(testIpRuleId)).thenReturn(Optional.of(testIpRule));
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
            when(ipRuleRepository.save(any(IpRule.class))).thenReturn(testIpRule);

            // When
            IpRuleResponse response = ipRuleService.update(testIpRuleId, request);

            // Then
            assertThat(response).isNotNull();
            verify(ipRuleRepository).save(testIpRule);

            assertThat(testIpRule.getIpAddress()).isEqualTo("10.0.0.1");
            assertThat(testIpRule.getRuleType()).isEqualTo(RuleType.BLACKLIST);
            assertThat(testIpRule.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent rule")
        void update_ruleNotFound_throwsException() {
            // Given
            IpRuleRequest request = new IpRuleRequest(
                    "10.0.0.1",
                    null,
                    RuleType.BLACKLIST,
                    testPolicyId,
                    testTenantId,
                    "Updated rule",
                    true
            );

            when(ipRuleRepository.findById(testIpRuleId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> ipRuleService.update(testIpRuleId, request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("IP rule not found with id: " + testIpRuleId);
        }
    }

    @Nested
    @DisplayName("Delete Operation Tests")
    class DeleteOperationTests {

        @Test
        @DisplayName("Should delete IP rule successfully")
        void delete_ruleExists_deletesRule() {
            // Given
            when(ipRuleRepository.existsById(testIpRuleId)).thenReturn(true);

            // When
            ipRuleService.delete(testIpRuleId);

            // Then
            verify(ipRuleRepository).deleteById(testIpRuleId);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent rule")
        void delete_ruleNotFound_throwsException() {
            // Given
            when(ipRuleRepository.existsById(testIpRuleId)).thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> ipRuleService.delete(testIpRuleId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("IP rule not found with id: " + testIpRuleId);

            verify(ipRuleRepository, never()).deleteById(any());
        }
    }
}
