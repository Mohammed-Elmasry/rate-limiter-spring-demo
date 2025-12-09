package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.api.exception.GlobalExceptionHandler;
import com.example.ratelimiter.application.dto.PolicyRequest;
import com.example.ratelimiter.application.dto.PolicyResponse;
import com.example.ratelimiter.application.service.PolicyService;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PolicyController.class, GlobalExceptionHandler.class})
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PolicyService policyService;

    private PolicyResponse createPolicyResponse(UUID id, String name) {
        return PolicyResponse.builder()
                .id(id)
                .name(name)
                .description("Test policy")
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .burstCapacity(120)
                .refillRate(BigDecimal.valueOf(1.67))
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .isDefault(false)
                .tenantId(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private PolicyRequest createValidPolicyRequest(String name) {
        return PolicyRequest.builder()
                .name(name)
                .description("Test policy")
                .scope(PolicyScope.USER)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .burstCapacity(120)
                .refillRate(BigDecimal.valueOf(1.67))
                .failMode(FailMode.FAIL_CLOSED)
                .enabled(true)
                .isDefault(false)
                .build();
    }

    @Nested
    @DisplayName("GET /api/policies")
    class FindAllTests {

        @Test
        @DisplayName("should return empty list when no policies exist")
        void shouldReturnEmptyList() throws Exception {
            when(policyService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/policies"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(policyService).findAll();
        }

        @Test
        @DisplayName("should return all policies")
        void shouldReturnAllPolicies() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<PolicyResponse> policies = List.of(
                    createPolicyResponse(id1, "policy-1"),
                    createPolicyResponse(id2, "policy-2")
            );

            when(policyService.findAll()).thenReturn(policies);

            mockMvc.perform(get("/api/policies"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(id1.toString())))
                    .andExpect(jsonPath("$[0].name", is("policy-1")))
                    .andExpect(jsonPath("$[1].id", is(id2.toString())))
                    .andExpect(jsonPath("$[1].name", is("policy-2")));

            verify(policyService).findAll();
        }
    }

    @Nested
    @DisplayName("GET /api/policies/{id}")
    class FindByIdTests {

        @Test
        @DisplayName("should return policy when exists")
        void shouldReturnPolicyWhenExists() throws Exception {
            UUID id = UUID.randomUUID();
            PolicyResponse policy = createPolicyResponse(id, "test-policy");

            when(policyService.findById(id)).thenReturn(policy);

            mockMvc.perform(get("/api/policies/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("test-policy")))
                    .andExpect(jsonPath("$.scope", is("USER")))
                    .andExpect(jsonPath("$.algorithm", is("TOKEN_BUCKET")))
                    .andExpect(jsonPath("$.maxRequests", is(100)))
                    .andExpect(jsonPath("$.windowSeconds", is(60)));

            verify(policyService).findById(id);
        }

        @Test
        @DisplayName("should return 404 when policy not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            when(policyService.findById(id))
                    .thenThrow(new EntityNotFoundException("Policy not found with id: " + id));

            mockMvc.perform(get("/api/policies/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(id.toString())));

            verify(policyService).findById(id);
        }
    }

    @Nested
    @DisplayName("GET /api/policies/tenant/{tenantId}")
    class FindByTenantIdTests {

        @Test
        @DisplayName("should return policies for tenant")
        void shouldReturnPoliciesForTenant() throws Exception {
            UUID tenantId = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            PolicyResponse policy = PolicyResponse.builder()
                    .id(policyId)
                    .name("tenant-policy")
                    .scope(PolicyScope.TENANT)
                    .algorithm(Algorithm.FIXED_WINDOW)
                    .maxRequests(50)
                    .windowSeconds(30)
                    .failMode(FailMode.FAIL_CLOSED)
                    .enabled(true)
                    .isDefault(false)
                    .tenantId(tenantId)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(policyService.findByTenantId(tenantId)).thenReturn(List.of(policy));

            mockMvc.perform(get("/api/policies/tenant/{tenantId}", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].tenantId", is(tenantId.toString())));

            verify(policyService).findByTenantId(tenantId);
        }
    }

    @Nested
    @DisplayName("POST /api/policies")
    class CreateTests {

        @Test
        @DisplayName("should create policy with valid request")
        void shouldCreatePolicyWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            PolicyRequest request = createValidPolicyRequest("new-policy");
            PolicyResponse response = createPolicyResponse(id, "new-policy");

            when(policyService.create(any(PolicyRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("new-policy")));

            verify(policyService).create(any(PolicyRequest.class));
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400WhenNameBlank() throws Exception {
            PolicyRequest request = PolicyRequest.builder()
                    .name("")
                    .scope(PolicyScope.USER)
                    .algorithm(Algorithm.TOKEN_BUCKET)
                    .maxRequests(100)
                    .windowSeconds(60)
                    .build();

            mockMvc.perform(post("/api/policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Error")))
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(policyService);
        }

        @Test
        @DisplayName("should return 400 when scope is missing")
        void shouldReturn400WhenScopeMissing() throws Exception {
            String requestJson = """
                {
                    "name": "test-policy",
                    "algorithm": "TOKEN_BUCKET",
                    "maxRequests": 100,
                    "windowSeconds": 60
                }
                """;

            mockMvc.perform(post("/api/policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.scope", notNullValue()));

            verifyNoInteractions(policyService);
        }

        @Test
        @DisplayName("should return 400 when maxRequests is zero")
        void shouldReturn400WhenMaxRequestsZero() throws Exception {
            PolicyRequest request = PolicyRequest.builder()
                    .name("test-policy")
                    .scope(PolicyScope.USER)
                    .algorithm(Algorithm.TOKEN_BUCKET)
                    .maxRequests(0)
                    .windowSeconds(60)
                    .build();

            mockMvc.perform(post("/api/policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.maxRequests", notNullValue()));

            verifyNoInteractions(policyService);
        }

        @Test
        @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenTenantNotFound() throws Exception {
            UUID tenantId = UUID.randomUUID();
            PolicyRequest request = PolicyRequest.builder()
                    .name("test-policy")
                    .scope(PolicyScope.TENANT)
                    .algorithm(Algorithm.TOKEN_BUCKET)
                    .maxRequests(100)
                    .windowSeconds(60)
                    .tenantId(tenantId)
                    .build();

            when(policyService.create(any(PolicyRequest.class)))
                    .thenThrow(new EntityNotFoundException("Tenant not found with id: " + tenantId));

            mockMvc.perform(post("/api/policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }
    }

    @Nested
    @DisplayName("PUT /api/policies/{id}")
    class UpdateTests {

        @Test
        @DisplayName("should update policy with valid request")
        void shouldUpdatePolicyWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            PolicyRequest request = createValidPolicyRequest("updated-policy");
            PolicyResponse response = createPolicyResponse(id, "updated-policy");

            when(policyService.update(eq(id), any(PolicyRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/policies/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("updated-policy")));

            verify(policyService).update(eq(id), any(PolicyRequest.class));
        }

        @Test
        @DisplayName("should return 404 when policy not found")
        void shouldReturn404WhenPolicyNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            PolicyRequest request = createValidPolicyRequest("updated-policy");

            when(policyService.update(eq(id), any(PolicyRequest.class)))
                    .thenThrow(new EntityNotFoundException("Policy not found with id: " + id));

            mockMvc.perform(put("/api/policies/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            UUID id = UUID.randomUUID();
            PolicyRequest request = PolicyRequest.builder()
                    .name("")
                    .scope(PolicyScope.USER)
                    .algorithm(Algorithm.TOKEN_BUCKET)
                    .maxRequests(100)
                    .windowSeconds(60)
                    .build();

            mockMvc.perform(put("/api/policies/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(policyService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/policies/{id}")
    class DeleteTests {

        @Test
        @DisplayName("should delete policy when exists")
        void shouldDeletePolicyWhenExists() throws Exception {
            UUID id = UUID.randomUUID();

            doNothing().when(policyService).delete(id);

            mockMvc.perform(delete("/api/policies/{id}", id))
                    .andExpect(status().isNoContent());

            verify(policyService).delete(id);
        }

        @Test
        @DisplayName("should return 404 when policy not found")
        void shouldReturn404WhenPolicyNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new EntityNotFoundException("Policy not found with id: " + id))
                    .when(policyService).delete(id);

            mockMvc.perform(delete("/api/policies/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));

            verify(policyService).delete(id);
        }
    }
}
