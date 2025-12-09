package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.api.exception.GlobalExceptionHandler;
import com.example.ratelimiter.application.dto.ApiKeyCreatedResponse;
import com.example.ratelimiter.application.dto.ApiKeyRequest;
import com.example.ratelimiter.application.dto.ApiKeyResponse;
import com.example.ratelimiter.application.service.ApiKeyService;
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

@WebMvcTest(controllers = {ApiKeyController.class, GlobalExceptionHandler.class})
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApiKeyService apiKeyService;

    private ApiKeyResponse createApiKeyResponse(UUID id, String name) {
        return ApiKeyResponse.builder()
                .id(id)
                .keyPrefix("sk_test")
                .name(name)
                .tenantId(UUID.randomUUID())
                .tenantName("Test Tenant")
                .policyId(UUID.randomUUID())
                .policyName("Test Policy")
                .enabled(true)
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .lastUsedAt(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private ApiKeyCreatedResponse createApiKeyCreatedResponse(UUID id, String name, String apiKey) {
        return ApiKeyCreatedResponse.builder()
                .id(id)
                .apiKey(apiKey)
                .keyPrefix("sk_test")
                .name(name)
                .tenantId(UUID.randomUUID())
                .tenantName("Test Tenant")
                .policyId(UUID.randomUUID())
                .policyName("Test Policy")
                .enabled(true)
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .createdAt(OffsetDateTime.now())
                .warning("Store this API key securely. It cannot be retrieved again.")
                .build();
    }

    private ApiKeyRequest createValidApiKeyRequest(String name, UUID tenantId) {
        return ApiKeyRequest.builder()
                .name(name)
                .tenantId(tenantId)
                .policyId(UUID.randomUUID())
                .enabled(true)
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();
    }

    @Nested
    @DisplayName("GET /api/api-keys")
    class FindAllTests {

        @Test
        @DisplayName("should return empty list when no API keys exist")
        void shouldReturnEmptyList() throws Exception {
            when(apiKeyService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/api-keys"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(apiKeyService).findAll();
        }

        @Test
        @DisplayName("should return all API keys")
        void shouldReturnAllApiKeys() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<ApiKeyResponse> apiKeys = List.of(
                    createApiKeyResponse(id1, "api-key-1"),
                    createApiKeyResponse(id2, "api-key-2")
            );

            when(apiKeyService.findAll()).thenReturn(apiKeys);

            mockMvc.perform(get("/api/api-keys"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(id1.toString())))
                    .andExpect(jsonPath("$[0].name", is("api-key-1")))
                    .andExpect(jsonPath("$[0].keyPrefix", is("sk_test")))
                    .andExpect(jsonPath("$[1].id", is(id2.toString())))
                    .andExpect(jsonPath("$[1].name", is("api-key-2")));

            verify(apiKeyService).findAll();
        }
    }

    @Nested
    @DisplayName("GET /api/api-keys/{id}")
    class FindByIdTests {

        @Test
        @DisplayName("should return API key when exists")
        void shouldReturnApiKeyWhenExists() throws Exception {
            UUID id = UUID.randomUUID();
            ApiKeyResponse apiKey = createApiKeyResponse(id, "test-api-key");

            when(apiKeyService.findById(id)).thenReturn(apiKey);

            mockMvc.perform(get("/api/api-keys/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("test-api-key")))
                    .andExpect(jsonPath("$.keyPrefix", is("sk_test")))
                    .andExpect(jsonPath("$.enabled", is(true)))
                    .andExpect(jsonPath("$.tenantName", is("Test Tenant")))
                    .andExpect(jsonPath("$.policyName", is("Test Policy")));

            verify(apiKeyService).findById(id);
        }

        @Test
        @DisplayName("should return 404 when API key not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            when(apiKeyService.findById(id))
                    .thenThrow(new EntityNotFoundException("API key not found with id: " + id));

            mockMvc.perform(get("/api/api-keys/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(id.toString())));

            verify(apiKeyService).findById(id);
        }

        @Test
        @DisplayName("should return 400 when id is not a valid UUID")
        void shouldReturn400WhenIdInvalid() throws Exception {
            mockMvc.perform(get("/api/api-keys/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(apiKeyService);
        }
    }

    @Nested
    @DisplayName("POST /api/api-keys")
    class CreateTests {

        @Test
        @DisplayName("should create API key with valid request")
        void shouldCreateApiKeyWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            String apiKey = "sk_test_1234567890abcdef";
            ApiKeyRequest request = createValidApiKeyRequest("new-api-key", tenantId);
            ApiKeyCreatedResponse response = createApiKeyCreatedResponse(id, "new-api-key", apiKey);

            when(apiKeyService.create(any(ApiKeyRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("new-api-key")))
                    .andExpect(jsonPath("$.apiKey", is(apiKey)))
                    .andExpect(jsonPath("$.keyPrefix", is("sk_test")))
                    .andExpect(jsonPath("$.warning", notNullValue()));

            verify(apiKeyService).create(any(ApiKeyRequest.class));
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400WhenNameBlank() throws Exception {
            UUID tenantId = UUID.randomUUID();
            ApiKeyRequest request = ApiKeyRequest.builder()
                    .name("")
                    .tenantId(tenantId)
                    .build();

            mockMvc.perform(post("/api/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Error")))
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(apiKeyService);
        }

        @Test
        @DisplayName("should return 400 when name is null")
        void shouldReturn400WhenNameNull() throws Exception {
            UUID tenantId = UUID.randomUUID();
            String requestJson = String.format("""
                {
                    "tenantId": "%s",
                    "enabled": true
                }
                """, tenantId);

            mockMvc.perform(post("/api/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(apiKeyService);
        }

        @Test
        @DisplayName("should return 400 when tenantId is null")
        void shouldReturn400WhenTenantIdNull() throws Exception {
            String requestJson = """
                {
                    "name": "test-api-key",
                    "enabled": true
                }
                """;

            mockMvc.perform(post("/api/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.tenantId", notNullValue()));

            verifyNoInteractions(apiKeyService);
        }

        @Test
        @DisplayName("should return 400 when name exceeds maximum length")
        void shouldReturn400WhenNameTooLong() throws Exception {
            UUID tenantId = UUID.randomUUID();
            String longName = "a".repeat(256);
            ApiKeyRequest request = ApiKeyRequest.builder()
                    .name(longName)
                    .tenantId(tenantId)
                    .build();

            mockMvc.perform(post("/api/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(apiKeyService);
        }

        @Test
        @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenTenantNotFound() throws Exception {
            UUID tenantId = UUID.randomUUID();
            ApiKeyRequest request = createValidApiKeyRequest("test-api-key", tenantId);

            when(apiKeyService.create(any(ApiKeyRequest.class)))
                    .thenThrow(new EntityNotFoundException("Tenant not found with id: " + tenantId));

            mockMvc.perform(post("/api/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should create API key with optional fields null")
        void shouldCreateApiKeyWithOptionalFieldsNull() throws Exception {
            UUID id = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            String apiKey = "sk_test_1234567890abcdef";
            ApiKeyRequest request = ApiKeyRequest.builder()
                    .name("minimal-api-key")
                    .tenantId(tenantId)
                    .build();
            ApiKeyCreatedResponse response = createApiKeyCreatedResponse(id, "minimal-api-key", apiKey);

            when(apiKeyService.create(any(ApiKeyRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.apiKey", is(apiKey)));

            verify(apiKeyService).create(any(ApiKeyRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /api/api-keys/{id}")
    class UpdateTests {

        @Test
        @DisplayName("should update API key with valid request")
        void shouldUpdateApiKeyWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            ApiKeyRequest request = createValidApiKeyRequest("updated-api-key", tenantId);
            ApiKeyResponse response = createApiKeyResponse(id, "updated-api-key");

            when(apiKeyService.update(eq(id), any(ApiKeyRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/api-keys/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("updated-api-key")));

            verify(apiKeyService).update(eq(id), any(ApiKeyRequest.class));
        }

        @Test
        @DisplayName("should return 404 when API key not found")
        void shouldReturn404WhenApiKeyNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            ApiKeyRequest request = createValidApiKeyRequest("updated-api-key", tenantId);

            when(apiKeyService.update(eq(id), any(ApiKeyRequest.class)))
                    .thenThrow(new EntityNotFoundException("API key not found with id: " + id));

            mockMvc.perform(put("/api/api-keys/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            UUID id = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            ApiKeyRequest request = ApiKeyRequest.builder()
                    .name("")
                    .tenantId(tenantId)
                    .build();

            mockMvc.perform(put("/api/api-keys/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(apiKeyService);
        }

        @Test
        @DisplayName("should return 400 when tenantId is missing")
        void shouldReturn400WhenTenantIdMissing() throws Exception {
            UUID id = UUID.randomUUID();
            String requestJson = """
                {
                    "name": "updated-api-key",
                    "enabled": false
                }
                """;

            mockMvc.perform(put("/api/api-keys/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.tenantId", notNullValue()));

            verifyNoInteractions(apiKeyService);
        }

        @Test
        @DisplayName("should update API key with disabled status")
        void shouldUpdateApiKeyWithDisabledStatus() throws Exception {
            UUID id = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            ApiKeyRequest request = ApiKeyRequest.builder()
                    .name("disabled-api-key")
                    .tenantId(tenantId)
                    .enabled(false)
                    .build();
            ApiKeyResponse response = ApiKeyResponse.builder()
                    .id(id)
                    .keyPrefix("sk_test")
                    .name("disabled-api-key")
                    .tenantId(tenantId)
                    .tenantName("Test Tenant")
                    .enabled(false)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(apiKeyService.update(eq(id), any(ApiKeyRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/api-keys/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled", is(false)));

            verify(apiKeyService).update(eq(id), any(ApiKeyRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/api-keys/{id}")
    class DeleteTests {

        @Test
        @DisplayName("should delete API key when exists")
        void shouldDeleteApiKeyWhenExists() throws Exception {
            UUID id = UUID.randomUUID();

            doNothing().when(apiKeyService).delete(id);

            mockMvc.perform(delete("/api/api-keys/{id}", id))
                    .andExpect(status().isNoContent());

            verify(apiKeyService).delete(id);
        }

        @Test
        @DisplayName("should return 404 when API key not found")
        void shouldReturn404WhenApiKeyNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new EntityNotFoundException("API key not found with id: " + id))
                    .when(apiKeyService).delete(id);

            mockMvc.perform(delete("/api/api-keys/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));

            verify(apiKeyService).delete(id);
        }

        @Test
        @DisplayName("should return 400 when id is not a valid UUID")
        void shouldReturn400WhenIdInvalid() throws Exception {
            mockMvc.perform(delete("/api/api-keys/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(apiKeyService);
        }
    }
}
