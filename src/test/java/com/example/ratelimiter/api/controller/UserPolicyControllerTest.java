package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.api.exception.GlobalExceptionHandler;
import com.example.ratelimiter.application.dto.UserPolicyRequest;
import com.example.ratelimiter.application.dto.UserPolicyResponse;
import com.example.ratelimiter.application.service.UserPolicyService;
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

@WebMvcTest(controllers = {UserPolicyController.class, GlobalExceptionHandler.class})
class UserPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserPolicyService userPolicyService;

    private UserPolicyResponse createUserPolicyResponse(UUID id, String userId) {
        return UserPolicyResponse.builder()
                .id(id)
                .userId(userId)
                .policyId(UUID.randomUUID())
                .policyName("Test Policy")
                .tenantId(UUID.randomUUID())
                .tenantName("Test Tenant")
                .enabled(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private UserPolicyRequest createValidUserPolicyRequest(String userId, UUID policyId, UUID tenantId) {
        return UserPolicyRequest.builder()
                .userId(userId)
                .policyId(policyId)
                .tenantId(tenantId)
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("GET /api/user-policies")
    class FindAllTests {

        @Test
        @DisplayName("should return empty list when no user policies exist")
        void shouldReturnEmptyList() throws Exception {
            when(userPolicyService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/user-policies"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(userPolicyService).findAll();
        }

        @Test
        @DisplayName("should return all user policies")
        void shouldReturnAllUserPolicies() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<UserPolicyResponse> userPolicies = List.of(
                    createUserPolicyResponse(id1, "user-1"),
                    createUserPolicyResponse(id2, "user-2")
            );

            when(userPolicyService.findAll()).thenReturn(userPolicies);

            mockMvc.perform(get("/api/user-policies"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(id1.toString())))
                    .andExpect(jsonPath("$[0].userId", is("user-1")))
                    .andExpect(jsonPath("$[1].id", is(id2.toString())))
                    .andExpect(jsonPath("$[1].userId", is("user-2")));

            verify(userPolicyService).findAll();
        }
    }

    @Nested
    @DisplayName("GET /api/user-policies/{id}")
    class FindByIdTests {

        @Test
        @DisplayName("should return user policy when exists")
        void shouldReturnUserPolicyWhenExists() throws Exception {
            UUID id = UUID.randomUUID();
            UserPolicyResponse userPolicy = createUserPolicyResponse(id, "test-user");

            when(userPolicyService.findById(id)).thenReturn(userPolicy);

            mockMvc.perform(get("/api/user-policies/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.userId", is("test-user")))
                    .andExpect(jsonPath("$.enabled", is(true)))
                    .andExpect(jsonPath("$.policyName", is("Test Policy")))
                    .andExpect(jsonPath("$.tenantName", is("Test Tenant")));

            verify(userPolicyService).findById(id);
        }

        @Test
        @DisplayName("should return 404 when user policy not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            when(userPolicyService.findById(id))
                    .thenThrow(new EntityNotFoundException("User policy not found with id: " + id));

            mockMvc.perform(get("/api/user-policies/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(id.toString())));

            verify(userPolicyService).findById(id);
        }

        @Test
        @DisplayName("should return 400 when id is not a valid UUID")
        void shouldReturn400WhenIdInvalid() throws Exception {
            mockMvc.perform(get("/api/user-policies/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(userPolicyService);
        }
    }

    @Nested
    @DisplayName("GET /api/user-policies/user/{userId}")
    class FindByUserIdTests {

        @Test
        @DisplayName("should return user policy for user and tenant")
        void shouldReturnUserPolicyForUserAndTenant() throws Exception {
            String userId = "test-user";
            UUID tenantId = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UserPolicyResponse userPolicy = UserPolicyResponse.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .policyId(policyId)
                    .policyName("Custom User Policy")
                    .tenantId(tenantId)
                    .tenantName("Test Tenant")
                    .enabled(true)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(userPolicyService.findByUserId(userId, tenantId)).thenReturn(userPolicy);

            mockMvc.perform(get("/api/user-policies/user/{userId}", userId)
                            .param("tenantId", tenantId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId", is(userId)))
                    .andExpect(jsonPath("$.tenantId", is(tenantId.toString())))
                    .andExpect(jsonPath("$.policyName", is("Custom User Policy")));

            verify(userPolicyService).findByUserId(userId, tenantId);
        }

        @Test
        @DisplayName("should return 404 when user policy not found for user")
        void shouldReturn404WhenUserPolicyNotFoundForUser() throws Exception {
            String userId = "non-existent-user";
            UUID tenantId = UUID.randomUUID();

            when(userPolicyService.findByUserId(userId, tenantId))
                    .thenThrow(new EntityNotFoundException("User policy not found for userId: " + userId));

            mockMvc.perform(get("/api/user-policies/user/{userId}", userId)
                            .param("tenantId", tenantId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));

            verify(userPolicyService).findByUserId(userId, tenantId);
        }

        @Test
        @DisplayName("should return 400 when tenantId query parameter is missing")
        void shouldReturn400WhenTenantIdMissing() throws Exception {
            String userId = "test-user";

            mockMvc.perform(get("/api/user-policies/user/{userId}", userId))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userPolicyService);
        }

        @Test
        @DisplayName("should return 400 when tenantId query parameter is not a valid UUID")
        void shouldReturn400WhenTenantIdInvalid() throws Exception {
            String userId = "test-user";

            mockMvc.perform(get("/api/user-policies/user/{userId}", userId)
                            .param("tenantId", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(userPolicyService);
        }
    }

    @Nested
    @DisplayName("POST /api/user-policies")
    class CreateTests {

        @Test
        @DisplayName("should create user policy with valid request")
        void shouldCreateUserPolicyWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            String userId = "new-user";
            UserPolicyRequest request = createValidUserPolicyRequest(userId, policyId, tenantId);
            UserPolicyResponse response = createUserPolicyResponse(id, userId);

            when(userPolicyService.create(any(UserPolicyRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.userId", is(userId)));

            verify(userPolicyService).create(any(UserPolicyRequest.class));
        }

        @Test
        @DisplayName("should return 400 when userId is blank")
        void shouldReturn400WhenUserIdBlank() throws Exception {
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UserPolicyRequest request = UserPolicyRequest.builder()
                    .userId("")
                    .policyId(policyId)
                    .tenantId(tenantId)
                    .build();

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Error")))
                    .andExpect(jsonPath("$.errors.userId", notNullValue()));

            verifyNoInteractions(userPolicyService);
        }

        @Test
        @DisplayName("should return 400 when userId is null")
        void shouldReturn400WhenUserIdNull() throws Exception {
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            String requestJson = String.format("""
                {
                    "policyId": "%s",
                    "tenantId": "%s",
                    "enabled": true
                }
                """, policyId, tenantId);

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.userId", notNullValue()));

            verifyNoInteractions(userPolicyService);
        }

        @Test
        @DisplayName("should return 400 when policyId is null")
        void shouldReturn400WhenPolicyIdNull() throws Exception {
            UUID tenantId = UUID.randomUUID();
            String requestJson = String.format("""
                {
                    "userId": "test-user",
                    "tenantId": "%s",
                    "enabled": true
                }
                """, tenantId);

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.policyId", notNullValue()));

            verifyNoInteractions(userPolicyService);
        }

        @Test
        @DisplayName("should return 400 when tenantId is null")
        void shouldReturn400WhenTenantIdNull() throws Exception {
            UUID policyId = UUID.randomUUID();
            String requestJson = String.format("""
                {
                    "userId": "test-user",
                    "policyId": "%s",
                    "enabled": true
                }
                """, policyId);

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.tenantId", notNullValue()));

            verifyNoInteractions(userPolicyService);
        }

        @Test
        @DisplayName("should return 400 when userId exceeds maximum length")
        void shouldReturn400WhenUserIdTooLong() throws Exception {
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            String longUserId = "a".repeat(256);
            UserPolicyRequest request = UserPolicyRequest.builder()
                    .userId(longUserId)
                    .policyId(policyId)
                    .tenantId(tenantId)
                    .build();

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.userId", notNullValue()));

            verifyNoInteractions(userPolicyService);
        }

        @Test
        @DisplayName("should return 404 when policy not found")
        void shouldReturn404WhenPolicyNotFound() throws Exception {
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UserPolicyRequest request = createValidUserPolicyRequest("test-user", policyId, tenantId);

            when(userPolicyService.create(any(UserPolicyRequest.class)))
                    .thenThrow(new EntityNotFoundException("Policy not found with id: " + policyId));

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenTenantNotFound() throws Exception {
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UserPolicyRequest request = createValidUserPolicyRequest("test-user", policyId, tenantId);

            when(userPolicyService.create(any(UserPolicyRequest.class)))
                    .thenThrow(new EntityNotFoundException("Tenant not found with id: " + tenantId));

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should create user policy with enabled defaulting to true")
        void shouldCreateUserPolicyWithDefaultEnabled() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            String userId = "test-user";
            UserPolicyRequest request = UserPolicyRequest.builder()
                    .userId(userId)
                    .policyId(policyId)
                    .tenantId(tenantId)
                    .build();
            UserPolicyResponse response = createUserPolicyResponse(id, userId);

            when(userPolicyService.create(any(UserPolicyRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/user-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.enabled", is(true)));

            verify(userPolicyService).create(any(UserPolicyRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /api/user-policies/{id}")
    class UpdateTests {

        @Test
        @DisplayName("should update user policy with valid request")
        void shouldUpdateUserPolicyWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UserPolicyRequest request = createValidUserPolicyRequest("updated-user", policyId, tenantId);
            UserPolicyResponse response = createUserPolicyResponse(id, "updated-user");

            when(userPolicyService.update(eq(id), any(UserPolicyRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/user-policies/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.userId", is("updated-user")));

            verify(userPolicyService).update(eq(id), any(UserPolicyRequest.class));
        }

        @Test
        @DisplayName("should return 404 when user policy not found")
        void shouldReturn404WhenUserPolicyNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UserPolicyRequest request = createValidUserPolicyRequest("updated-user", policyId, tenantId);

            when(userPolicyService.update(eq(id), any(UserPolicyRequest.class)))
                    .thenThrow(new EntityNotFoundException("User policy not found with id: " + id));

            mockMvc.perform(put("/api/user-policies/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UserPolicyRequest request = UserPolicyRequest.builder()
                    .userId("")
                    .policyId(policyId)
                    .tenantId(tenantId)
                    .build();

            mockMvc.perform(put("/api/user-policies/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.userId", notNullValue()));

            verifyNoInteractions(userPolicyService);
        }

        @Test
        @DisplayName("should update user policy to disabled status")
        void shouldUpdateUserPolicyToDisabled() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UserPolicyRequest request = UserPolicyRequest.builder()
                    .userId("test-user")
                    .policyId(policyId)
                    .tenantId(tenantId)
                    .enabled(false)
                    .build();
            UserPolicyResponse response = UserPolicyResponse.builder()
                    .id(id)
                    .userId("test-user")
                    .policyId(policyId)
                    .tenantId(tenantId)
                    .enabled(false)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(userPolicyService.update(eq(id), any(UserPolicyRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/user-policies/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled", is(false)));

            verify(userPolicyService).update(eq(id), any(UserPolicyRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/user-policies/{id}")
    class DeleteTests {

        @Test
        @DisplayName("should delete user policy when exists")
        void shouldDeleteUserPolicyWhenExists() throws Exception {
            UUID id = UUID.randomUUID();

            doNothing().when(userPolicyService).delete(id);

            mockMvc.perform(delete("/api/user-policies/{id}", id))
                    .andExpect(status().isNoContent());

            verify(userPolicyService).delete(id);
        }

        @Test
        @DisplayName("should return 404 when user policy not found")
        void shouldReturn404WhenUserPolicyNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new EntityNotFoundException("User policy not found with id: " + id))
                    .when(userPolicyService).delete(id);

            mockMvc.perform(delete("/api/user-policies/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));

            verify(userPolicyService).delete(id);
        }

        @Test
        @DisplayName("should return 400 when id is not a valid UUID")
        void shouldReturn400WhenIdInvalid() throws Exception {
            mockMvc.perform(delete("/api/user-policies/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(userPolicyService);
        }
    }
}
