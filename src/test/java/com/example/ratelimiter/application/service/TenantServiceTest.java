package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.TenantRequest;
import com.example.ratelimiter.application.dto.TenantResponse;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.enums.TenantTier;
import com.example.ratelimiter.domain.repository.TenantRepository;
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
@DisplayName("TenantService Tests")
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantService tenantService;

    private Tenant testTenant;
    private UUID testTenantId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();

        testTenant = Tenant.builder()
                .id(testTenantId)
                .name("test-tenant")
                .tier(TenantTier.PREMIUM)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Should create tenant successfully")
    void create_validRequest_createsTenant() {
        // Given
        TenantRequest request = new TenantRequest(
                "new-tenant",
                TenantTier.ENTERPRISE,
                true
        );

        when(tenantRepository.findByName("new-tenant")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenReturn(testTenant);

        // When
        TenantResponse response = tenantService.create(request);

        // Then
        assertThat(response).isNotNull();
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    @DisplayName("Should throw exception when tenant name already exists")
    void create_duplicateName_throwsException() {
        // Given
        TenantRequest request = new TenantRequest(
                "existing-tenant",
                TenantTier.PREMIUM,
                true
        );

        when(tenantRepository.findByName("existing-tenant")).thenReturn(Optional.of(testTenant));

        // When / Then
        assertThatThrownBy(() -> tenantService.create(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("already exists");

        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should find all tenants")
    void findAll_tenantsExist_returnsAll() {
        // Given
        when(tenantRepository.findAll()).thenReturn(Arrays.asList(testTenant));

        // When
        var responses = tenantService.findAll();

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("test-tenant");
    }

    @Test
    @DisplayName("Should find tenant by ID")
    void findById_tenantExists_returnsTenant() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));

        // When
        TenantResponse response = tenantService.findById(testTenantId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testTenantId);
    }

    @Test
    @DisplayName("Should throw exception when tenant not found")
    void findById_tenantNotFound_throwsException() {
        // Given
        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> tenantService.findById(testTenantId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Should update tenant successfully")
    void update_validRequest_updatesTenant() {
        // Given
        TenantRequest request = new TenantRequest(
                "updated-tenant",
                TenantTier.ENTERPRISE,
                false
        );

        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.findByName("updated-tenant")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenReturn(testTenant);

        // When
        TenantResponse response = tenantService.update(testTenantId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(testTenant.getName()).isEqualTo("updated-tenant");
        assertThat(testTenant.getTier()).isEqualTo(TenantTier.ENTERPRISE);
        assertThat(testTenant.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when updating to existing name")
    void update_duplicateName_throwsException() {
        // Given
        TenantRequest request = new TenantRequest(
                "other-tenant",
                TenantTier.PREMIUM,
                true
        );

        Tenant otherTenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("other-tenant")
                .build();

        when(tenantRepository.findById(testTenantId)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.findByName("other-tenant")).thenReturn(Optional.of(otherTenant));

        // When / Then
        assertThatThrownBy(() -> tenantService.update(testTenantId, request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should delete tenant successfully")
    void delete_tenantExists_deletesTenant() {
        // Given
        when(tenantRepository.existsById(testTenantId)).thenReturn(true);

        // When
        tenantService.delete(testTenantId);

        // Then
        verify(tenantRepository).deleteById(testTenantId);
    }
}
