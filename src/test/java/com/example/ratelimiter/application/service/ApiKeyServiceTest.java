package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.ApiKeyCreatedResponse;
import com.example.ratelimiter.application.dto.ApiKeyRequest;
import com.example.ratelimiter.application.dto.ApiKeyResponse;
import com.example.ratelimiter.domain.entity.ApiKey;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.repository.ApiKeyRepository;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyService Tests")
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private ApiKey testApiKey;
    private Tenant testTenant;
    private Policy testPolicy;
    private UUID testApiKeyId;
    private UUID testTenantId;
    private UUID testPolicyId;

    @BeforeEach
    void setUp() {
        testApiKeyId = UUID.randomUUID();
        testTenantId = UUID.randomUUID();
        testPolicyId = UUID.randomUUID();

        testTenant = Tenant.builder()
                .id(testTenantId)
                .name("test-tenant")
                .enabled(true)
                .build();

        testPolicy = Policy.builder()
                .id(testPolicyId)
                .name("test-policy")
                .build();

        testApiKey = ApiKey.builder()
                .id(testApiKeyId)
                .keyHash("hashvalue")
                .keyPrefix("rl_live_abc")
                .name("test-key")
                .tenant(testTenant)
                .policy(testPolicy)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Should create API key with generated key")
    void create_validRequest_generatesAndHashesKey() {
        // Given
        ApiKeyRequest request = new ApiKeyRequest(
                "My API Key",
                testTenantId,
                testPolicyId,
                true,
                OffsetDateTime.now().plusDays(30)
        );

        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // When
        ApiKeyCreatedResponse response = apiKeyService.create(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getApiKey()).startsWith("rl_live_");
        assertThat(response.getKeyPrefix()).isNotNull();
        assertThat(response.getId()).isEqualTo(testApiKeyId);

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());

        ApiKey savedKey = captor.getValue();
        assertThat(savedKey.getKeyHash()).isNotNull();
        assertThat(savedKey.getKeyPrefix()).isNotNull();
    }

    @Test
    @DisplayName("Should create API key without policy")
    void create_noPolicyId_createsWithoutPolicy() {
        // Given
        ApiKeyRequest request = new ApiKeyRequest(
                "My API Key",
                testTenantId,
                null,
                true,
                null
        );

        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // When
        ApiKeyCreatedResponse response = apiKeyService.create(request);

        // Then
        assertThat(response).isNotNull();
        verify(policyRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should throw exception when tenant not found")
    void create_tenantNotFound_throwsException() {
        // Given
        ApiKeyRequest request = new ApiKeyRequest(
                "My API Key",
                testTenantId,
                testPolicyId,
                true,
                null
        );

        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> apiKeyService.create(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    @DisplayName("Should find all API keys")
    void findAll_keysExist_returnsAll() {
        // Given
        when(apiKeyRepository.findAll()).thenReturn(Arrays.asList(testApiKey));

        // When
        var responses = apiKeyService.findAll();

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("test-key");
    }

    @Test
    @DisplayName("Should find API key by ID")
    void findById_keyExists_returnsKey() {
        // Given
        when(apiKeyRepository.findById(testApiKeyId)).thenReturn(Optional.of(testApiKey));

        // When
        ApiKeyResponse response = apiKeyService.findById(testApiKeyId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testApiKeyId);
    }

    @Test
    @DisplayName("Should update API key")
    void update_validRequest_updatesKey() {
        // Given
        ApiKeyRequest request = new ApiKeyRequest(
                "Updated Key",
                testTenantId,
                testPolicyId,
                false,
                OffsetDateTime.now().plusDays(60)
        );

        when(apiKeyRepository.findById(testApiKeyId)).thenReturn(Optional.of(testApiKey));
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // When
        ApiKeyResponse response = apiKeyService.update(testApiKeyId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(testApiKey.getName()).isEqualTo("Updated Key");
        assertThat(testApiKey.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should delete API key")
    void delete_keyExists_deletesKey() {
        // Given
        when(apiKeyRepository.existsById(testApiKeyId)).thenReturn(true);

        // When
        apiKeyService.delete(testApiKeyId);

        // Then
        verify(apiKeyRepository).deleteById(testApiKeyId);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent key")
    void delete_keyNotFound_throwsException() {
        // Given
        when(apiKeyRepository.existsById(testApiKeyId)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> apiKeyService.delete(testApiKeyId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
