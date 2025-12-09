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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPolicyService Tests")
class UserPolicyServiceTest {

    @Mock
    private UserPolicyRepository userPolicyRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private UserPolicyService userPolicyService;

    private UserPolicy testUserPolicy;
    private Policy testPolicy;
    private Tenant testTenant;
    private UUID testUserPolicyId;
    private UUID testPolicyId;
    private UUID testTenantId;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserPolicyId = UUID.randomUUID();
        testPolicyId = UUID.randomUUID();
        testTenantId = UUID.randomUUID();
        testUserId = "user123";

        testTenant = Tenant.builder()
                .id(testTenantId)
                .name("test-tenant")
                .build();

        testPolicy = Policy.builder()
                .id(testPolicyId)
                .name("test-policy")
                .build();

        testUserPolicy = UserPolicy.builder()
                .id(testUserPolicyId)
                .userId(testUserId)
                .policy(testPolicy)
                .tenant(testTenant)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Should create user policy")
    void create_validRequest_createsUserPolicy() {
        // Given
        UserPolicyRequest request = new UserPolicyRequest(
                "user456",
                testPolicyId,
                testTenantId,
                true
        );

        when(userPolicyRepository.existsByUserIdAndTenantId("user456", testTenantId)).thenReturn(false);
        when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(userPolicyRepository.save(any(UserPolicy.class))).thenReturn(testUserPolicy);

        // When
        UserPolicyResponse response = userPolicyService.create(request);

        // Then
        assertThat(response).isNotNull();
        verify(userPolicyRepository).save(any(UserPolicy.class));
    }

    @Test
    @DisplayName("Should throw exception when user policy already exists")
    void create_duplicateUserPolicy_throwsException() {
        // Given
        UserPolicyRequest request = new UserPolicyRequest(
                testUserId,
                testPolicyId,
                testTenantId,
                true
        );

        when(userPolicyRepository.existsByUserIdAndTenantId(testUserId, testTenantId)).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> userPolicyService.create(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should find all user policies")
    void findAll_policiesExist_returnsAll() {
        // Given
        when(userPolicyRepository.findAll()).thenReturn(Arrays.asList(testUserPolicy));

        // When
        var responses = userPolicyService.findAll();

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userId()).isEqualTo(testUserId);
    }

    @Test
    @DisplayName("Should find user policy by ID")
    void findById_policyExists_returnsPolicy() {
        // Given
        when(userPolicyRepository.findById(testUserPolicyId)).thenReturn(Optional.of(testUserPolicy));

        // When
        UserPolicyResponse response = userPolicyService.findById(testUserPolicyId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testUserPolicyId);
    }

    @Test
    @DisplayName("Should find user policy by user ID and tenant ID")
    void findByUserId_policyExists_returnsPolicy() {
        // Given
        when(userPolicyRepository.findByUserIdAndTenantId(testUserId, testTenantId))
                .thenReturn(Optional.of(testUserPolicy));

        // When
        UserPolicyResponse response = userPolicyService.findByUserId(testUserId, testTenantId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(testUserId);
        assertThat(response.tenantId()).isEqualTo(testTenantId);
    }

    @Test
    @DisplayName("Should update user policy")
    void update_validRequest_updatesPolicy() {
        // Given
        UserPolicyRequest request = new UserPolicyRequest(
                testUserId,
                testPolicyId,
                testTenantId,
                false
        );

        when(userPolicyRepository.findById(testUserPolicyId)).thenReturn(Optional.of(testUserPolicy));
        when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(userPolicyRepository.save(any(UserPolicy.class))).thenReturn(testUserPolicy);

        // When
        UserPolicyResponse response = userPolicyService.update(testUserPolicyId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(testUserPolicy.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should delete user policy")
    void delete_policyExists_deletesPolicy() {
        // Given
        when(userPolicyRepository.existsById(testUserPolicyId)).thenReturn(true);

        // When
        userPolicyService.delete(testUserPolicyId);

        // Then
        verify(userPolicyRepository).deleteById(testUserPolicyId);
    }
}
