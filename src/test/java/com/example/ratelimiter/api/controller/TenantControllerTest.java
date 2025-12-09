package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.api.exception.GlobalExceptionHandler;
import com.example.ratelimiter.application.dto.TenantRequest;
import com.example.ratelimiter.application.dto.TenantResponse;
import com.example.ratelimiter.application.service.TenantService;
import com.example.ratelimiter.domain.enums.TenantTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {TenantController.class, GlobalExceptionHandler.class})
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TenantService tenantService;

    private TenantResponse createTenantResponse(UUID id, String name, TenantTier tier) {
        return TenantResponse.builder()
                .id(id)
                .name(name)
                .tier(tier)
                .enabled(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private TenantRequest createValidTenantRequest(String name, TenantTier tier) {
        return TenantRequest.builder()
                .name(name)
                .tier(tier)
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("GET /api/tenants")
    class FindAllTests {

        @Test
        @DisplayName("should return empty list when no tenants exist")
        void shouldReturnEmptyList() throws Exception {
            when(tenantService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/tenants"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(tenantService).findAll();
        }

        @Test
        @DisplayName("should return all tenants")
        void shouldReturnAllTenants() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<TenantResponse> tenants = List.of(
                    createTenantResponse(id1, "tenant-1", TenantTier.FREE),
                    createTenantResponse(id2, "tenant-2", TenantTier.PREMIUM)
            );

            when(tenantService.findAll()).thenReturn(tenants);

            mockMvc.perform(get("/api/tenants"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(id1.toString())))
                    .andExpect(jsonPath("$[0].name", is("tenant-1")))
                    .andExpect(jsonPath("$[0].tier", is("FREE")))
                    .andExpect(jsonPath("$[1].id", is(id2.toString())))
                    .andExpect(jsonPath("$[1].name", is("tenant-2")))
                    .andExpect(jsonPath("$[1].tier", is("PREMIUM")));

            verify(tenantService).findAll();
        }
    }

    @Nested
    @DisplayName("GET /api/tenants/{id}")
    class FindByIdTests {

        @Test
        @DisplayName("should return tenant when exists")
        void shouldReturnTenantWhenExists() throws Exception {
            UUID id = UUID.randomUUID();
            TenantResponse tenant = createTenantResponse(id, "test-tenant", TenantTier.BASIC);

            when(tenantService.findById(id)).thenReturn(tenant);

            mockMvc.perform(get("/api/tenants/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("test-tenant")))
                    .andExpect(jsonPath("$.tier", is("BASIC")))
                    .andExpect(jsonPath("$.enabled", is(true)));

            verify(tenantService).findById(id);
        }

        @Test
        @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            when(tenantService.findById(id))
                    .thenThrow(new EntityNotFoundException("Tenant not found with id: " + id));

            mockMvc.perform(get("/api/tenants/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(id.toString())));

            verify(tenantService).findById(id);
        }

        @Test
        @DisplayName("should return 400 when id is not a valid UUID")
        void shouldReturn400WhenIdInvalid() throws Exception {
            mockMvc.perform(get("/api/tenants/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(tenantService);
        }
    }

    @Nested
    @DisplayName("POST /api/tenants")
    class CreateTests {

        @Test
        @DisplayName("should create tenant with valid request")
        void shouldCreateTenantWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            TenantRequest request = createValidTenantRequest("new-tenant", TenantTier.ENTERPRISE);
            TenantResponse response = createTenantResponse(id, "new-tenant", TenantTier.ENTERPRISE);

            when(tenantService.create(any(TenantRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("new-tenant")))
                    .andExpect(jsonPath("$.tier", is("ENTERPRISE")));

            verify(tenantService).create(any(TenantRequest.class));
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400WhenNameBlank() throws Exception {
            TenantRequest request = TenantRequest.builder()
                    .name("")
                    .tier(TenantTier.FREE)
                    .build();

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Error")))
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(tenantService);
        }

        @Test
        @DisplayName("should return 400 when name is null")
        void shouldReturn400WhenNameNull() throws Exception {
            String requestJson = """
                {
                    "tier": "FREE",
                    "enabled": true
                }
                """;

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(tenantService);
        }

        @Test
        @DisplayName("should return 400 when tier is null")
        void shouldReturn400WhenTierNull() throws Exception {
            String requestJson = """
                {
                    "name": "test-tenant",
                    "enabled": true
                }
                """;

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.tier", notNullValue()));

            verifyNoInteractions(tenantService);
        }

        @Test
        @DisplayName("should return 400 when name exceeds maximum length")
        void shouldReturn400WhenNameTooLong() throws Exception {
            String longName = "a".repeat(256);
            TenantRequest request = TenantRequest.builder()
                    .name(longName)
                    .tier(TenantTier.FREE)
                    .build();

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(tenantService);
        }

        @Test
        @DisplayName("should return 400 when tier has invalid value")
        void shouldReturn400WhenTierInvalid() throws Exception {
            String requestJson = """
                {
                    "name": "test-tenant",
                    "tier": "INVALID_TIER",
                    "enabled": true
                }
                """;

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Request Body")));

            verifyNoInteractions(tenantService);
        }

        @Test
        @DisplayName("should create tenant with enabled defaulting to true")
        void shouldCreateTenantWithDefaultEnabled() throws Exception {
            UUID id = UUID.randomUUID();
            TenantRequest request = TenantRequest.builder()
                    .name("test-tenant")
                    .tier(TenantTier.FREE)
                    .build();
            TenantResponse response = createTenantResponse(id, "test-tenant", TenantTier.FREE);

            when(tenantService.create(any(TenantRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.enabled", is(true)));

            verify(tenantService).create(any(TenantRequest.class));
        }

        @Test
        @DisplayName("should create tenant with all tier values")
        void shouldCreateTenantWithAllTierValues() throws Exception {
            for (TenantTier tier : TenantTier.values()) {
                UUID id = UUID.randomUUID();
                TenantRequest request = createValidTenantRequest("tenant-" + tier.name(), tier);
                TenantResponse response = createTenantResponse(id, "tenant-" + tier.name(), tier);

                when(tenantService.create(any(TenantRequest.class))).thenReturn(response);

                mockMvc.perform(post("/api/tenants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.tier", is(tier.name())));

                verify(tenantService, times(1)).create(any(TenantRequest.class));
                reset(tenantService);
            }
        }
    }

    @Nested
    @DisplayName("PUT /api/tenants/{id}")
    class UpdateTests {

        @Test
        @DisplayName("should update tenant with valid request")
        void shouldUpdateTenantWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            TenantRequest request = createValidTenantRequest("updated-tenant", TenantTier.PREMIUM);
            TenantResponse response = createTenantResponse(id, "updated-tenant", TenantTier.PREMIUM);

            when(tenantService.update(eq(id), any(TenantRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/tenants/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("updated-tenant")))
                    .andExpect(jsonPath("$.tier", is("PREMIUM")));

            verify(tenantService).update(eq(id), any(TenantRequest.class));
        }

        @Test
        @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenTenantNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            TenantRequest request = createValidTenantRequest("updated-tenant", TenantTier.BASIC);

            when(tenantService.update(eq(id), any(TenantRequest.class)))
                    .thenThrow(new EntityNotFoundException("Tenant not found with id: " + id));

            mockMvc.perform(put("/api/tenants/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            UUID id = UUID.randomUUID();
            TenantRequest request = TenantRequest.builder()
                    .name("")
                    .tier(TenantTier.FREE)
                    .build();

            mockMvc.perform(put("/api/tenants/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(tenantService);
        }

        @Test
        @DisplayName("should update tenant to disabled status")
        void shouldUpdateTenantToDisabled() throws Exception {
            UUID id = UUID.randomUUID();
            TenantRequest request = TenantRequest.builder()
                    .name("disabled-tenant")
                    .tier(TenantTier.FREE)
                    .enabled(false)
                    .build();
            TenantResponse response = TenantResponse.builder()
                    .id(id)
                    .name("disabled-tenant")
                    .tier(TenantTier.FREE)
                    .enabled(false)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(tenantService.update(eq(id), any(TenantRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/tenants/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled", is(false)));

            verify(tenantService).update(eq(id), any(TenantRequest.class));
        }

        @Test
        @DisplayName("should update tenant tier from FREE to ENTERPRISE")
        void shouldUpdateTenantTierFromFreeToEnterprise() throws Exception {
            UUID id = UUID.randomUUID();
            TenantRequest request = createValidTenantRequest("upgraded-tenant", TenantTier.ENTERPRISE);
            TenantResponse response = createTenantResponse(id, "upgraded-tenant", TenantTier.ENTERPRISE);

            when(tenantService.update(eq(id), any(TenantRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/tenants/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tier", is("ENTERPRISE")));

            verify(tenantService).update(eq(id), any(TenantRequest.class));
        }

        @Test
        @DisplayName("should return 400 when tier is missing in update")
        void shouldReturn400WhenTierMissing() throws Exception {
            UUID id = UUID.randomUUID();
            String requestJson = """
                {
                    "name": "updated-tenant",
                    "enabled": true
                }
                """;

            mockMvc.perform(put("/api/tenants/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.tier", notNullValue()));

            verifyNoInteractions(tenantService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/tenants/{id}")
    class DeleteTests {

        @Test
        @DisplayName("should delete tenant when exists")
        void shouldDeleteTenantWhenExists() throws Exception {
            UUID id = UUID.randomUUID();

            doNothing().when(tenantService).delete(id);

            mockMvc.perform(delete("/api/tenants/{id}", id))
                    .andExpect(status().isNoContent());

            verify(tenantService).delete(id);
        }

        @Test
        @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenTenantNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new EntityNotFoundException("Tenant not found with id: " + id))
                    .when(tenantService).delete(id);

            mockMvc.perform(delete("/api/tenants/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));

            verify(tenantService).delete(id);
        }

        @Test
        @DisplayName("should return 400 when id is not a valid UUID")
        void shouldReturn400WhenIdInvalid() throws Exception {
            mockMvc.perform(delete("/api/tenants/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(tenantService);
        }
    }
}
