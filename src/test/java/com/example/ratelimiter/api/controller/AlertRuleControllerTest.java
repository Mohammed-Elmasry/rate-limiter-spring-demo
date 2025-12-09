package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.api.exception.GlobalExceptionHandler;
import com.example.ratelimiter.application.dto.AlertRuleRequest;
import com.example.ratelimiter.application.dto.AlertRuleResponse;
import com.example.ratelimiter.application.service.AlertRuleService;
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

@WebMvcTest(controllers = {AlertRuleController.class, GlobalExceptionHandler.class})
class AlertRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AlertRuleService alertRuleService;

    private AlertRuleResponse createAlertRuleResponse(UUID id, String name, Integer thresholdPercentage) {
        return AlertRuleResponse.builder()
                .id(id)
                .name(name)
                .policyId(UUID.randomUUID())
                .policyName("Test Policy")
                .thresholdPercentage(thresholdPercentage)
                .windowSeconds(60)
                .cooldownSeconds(300)
                .enabled(true)
                .lastTriggeredAt(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private AlertRuleRequest createValidAlertRuleRequest(String name, Integer thresholdPercentage) {
        return AlertRuleRequest.builder()
                .name(name)
                .policyId(UUID.randomUUID())
                .thresholdPercentage(thresholdPercentage)
                .windowSeconds(60)
                .cooldownSeconds(300)
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("GET /api/alert-rules")
    class FindAllTests {

        @Test
        @DisplayName("should return empty list when no alert rules exist")
        void shouldReturnEmptyList() throws Exception {
            when(alertRuleService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/alert-rules"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(alertRuleService).findAll();
        }

        @Test
        @DisplayName("should return all alert rules")
        void shouldReturnAllAlertRules() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<AlertRuleResponse> alertRules = List.of(
                    createAlertRuleResponse(id1, "alert-rule-1", 80),
                    createAlertRuleResponse(id2, "alert-rule-2", 90)
            );

            when(alertRuleService.findAll()).thenReturn(alertRules);

            mockMvc.perform(get("/api/alert-rules"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(id1.toString())))
                    .andExpect(jsonPath("$[0].name", is("alert-rule-1")))
                    .andExpect(jsonPath("$[0].thresholdPercentage", is(80)))
                    .andExpect(jsonPath("$[1].id", is(id2.toString())))
                    .andExpect(jsonPath("$[1].name", is("alert-rule-2")))
                    .andExpect(jsonPath("$[1].thresholdPercentage", is(90)));

            verify(alertRuleService).findAll();
        }
    }

    @Nested
    @DisplayName("GET /api/alert-rules/{id}")
    class FindByIdTests {

        @Test
        @DisplayName("should return alert rule when exists")
        void shouldReturnAlertRuleWhenExists() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleResponse alertRule = createAlertRuleResponse(id, "test-alert-rule", 85);

            when(alertRuleService.findById(id)).thenReturn(alertRule);

            mockMvc.perform(get("/api/alert-rules/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("test-alert-rule")))
                    .andExpect(jsonPath("$.thresholdPercentage", is(85)))
                    .andExpect(jsonPath("$.windowSeconds", is(60)))
                    .andExpect(jsonPath("$.cooldownSeconds", is(300)))
                    .andExpect(jsonPath("$.enabled", is(true)));

            verify(alertRuleService).findById(id);
        }

        @Test
        @DisplayName("should return 404 when alert rule not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            when(alertRuleService.findById(id))
                    .thenThrow(new EntityNotFoundException("Alert rule not found with id: " + id));

            mockMvc.perform(get("/api/alert-rules/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(id.toString())));

            verify(alertRuleService).findById(id);
        }

        @Test
        @DisplayName("should return 400 when id is not a valid UUID")
        void shouldReturn400WhenIdInvalid() throws Exception {
            mockMvc.perform(get("/api/alert-rules/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(alertRuleService);
        }
    }

    @Nested
    @DisplayName("POST /api/alert-rules")
    class CreateTests {

        @Test
        @DisplayName("should create alert rule with valid request")
        void shouldCreateAlertRuleWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = createValidAlertRuleRequest("new-alert-rule", 75);
            AlertRuleResponse response = createAlertRuleResponse(id, "new-alert-rule", 75);

            when(alertRuleService.create(any(AlertRuleRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("new-alert-rule")))
                    .andExpect(jsonPath("$.thresholdPercentage", is(75)));

            verify(alertRuleService).create(any(AlertRuleRequest.class));
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400WhenNameBlank() throws Exception {
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("")
                    .thresholdPercentage(80)
                    .build();

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Error")))
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should return 400 when name is null")
        void shouldReturn400WhenNameNull() throws Exception {
            String requestJson = """
                {
                    "thresholdPercentage": 80,
                    "windowSeconds": 60,
                    "cooldownSeconds": 300
                }
                """;

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should return 400 when thresholdPercentage is null")
        void shouldReturn400WhenThresholdPercentageNull() throws Exception {
            String requestJson = """
                {
                    "name": "test-alert-rule",
                    "windowSeconds": 60,
                    "cooldownSeconds": 300
                }
                """;

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.thresholdPercentage", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should return 400 when thresholdPercentage is less than 1")
        void shouldReturn400WhenThresholdPercentageTooLow() throws Exception {
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("test-alert-rule")
                    .thresholdPercentage(0)
                    .build();

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.thresholdPercentage", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should return 400 when thresholdPercentage exceeds 100")
        void shouldReturn400WhenThresholdPercentageTooHigh() throws Exception {
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("test-alert-rule")
                    .thresholdPercentage(101)
                    .build();

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.thresholdPercentage", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should return 400 when windowSeconds is less than 1")
        void shouldReturn400WhenWindowSecondsTooLow() throws Exception {
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("test-alert-rule")
                    .thresholdPercentage(80)
                    .windowSeconds(0)
                    .build();

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.windowSeconds", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should return 400 when cooldownSeconds is negative")
        void shouldReturn400WhenCooldownSecondsNegative() throws Exception {
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("test-alert-rule")
                    .thresholdPercentage(80)
                    .cooldownSeconds(-1)
                    .build();

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.cooldownSeconds", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should return 400 when name exceeds maximum length")
        void shouldReturn400WhenNameTooLong() throws Exception {
            String longName = "a".repeat(256);
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name(longName)
                    .thresholdPercentage(80)
                    .build();

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should return 404 when policy not found")
        void shouldReturn404WhenPolicyNotFound() throws Exception {
            UUID policyId = UUID.randomUUID();
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("test-alert-rule")
                    .policyId(policyId)
                    .thresholdPercentage(80)
                    .build();

            when(alertRuleService.create(any(AlertRuleRequest.class)))
                    .thenThrow(new EntityNotFoundException("Policy not found with id: " + policyId));

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should create alert rule with default values for optional fields")
        void shouldCreateAlertRuleWithDefaults() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("minimal-alert-rule")
                    .thresholdPercentage(80)
                    .build();
            AlertRuleResponse response = createAlertRuleResponse(id, "minimal-alert-rule", 80);

            when(alertRuleService.create(any(AlertRuleRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())));

            verify(alertRuleService).create(any(AlertRuleRequest.class));
        }

        @Test
        @DisplayName("should create alert rule without policy")
        void shouldCreateAlertRuleWithoutPolicy() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("global-alert-rule")
                    .thresholdPercentage(90)
                    .windowSeconds(120)
                    .cooldownSeconds(600)
                    .enabled(true)
                    .build();
            AlertRuleResponse response = AlertRuleResponse.builder()
                    .id(id)
                    .name("global-alert-rule")
                    .policyId(null)
                    .policyName(null)
                    .thresholdPercentage(90)
                    .windowSeconds(120)
                    .cooldownSeconds(600)
                    .enabled(true)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(alertRuleService.create(any(AlertRuleRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/alert-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.policyId").doesNotExist());

            verify(alertRuleService).create(any(AlertRuleRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /api/alert-rules/{id}")
    class UpdateTests {

        @Test
        @DisplayName("should update alert rule with valid request")
        void shouldUpdateAlertRuleWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = createValidAlertRuleRequest("updated-alert-rule", 95);
            AlertRuleResponse response = createAlertRuleResponse(id, "updated-alert-rule", 95);

            when(alertRuleService.update(eq(id), any(AlertRuleRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/alert-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.name", is("updated-alert-rule")))
                    .andExpect(jsonPath("$.thresholdPercentage", is(95)));

            verify(alertRuleService).update(eq(id), any(AlertRuleRequest.class));
        }

        @Test
        @DisplayName("should return 404 when alert rule not found")
        void shouldReturn404WhenAlertRuleNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = createValidAlertRuleRequest("updated-alert-rule", 90);

            when(alertRuleService.update(eq(id), any(AlertRuleRequest.class)))
                    .thenThrow(new EntityNotFoundException("Alert rule not found with id: " + id));

            mockMvc.perform(put("/api/alert-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("")
                    .thresholdPercentage(80)
                    .build();

            mockMvc.perform(put("/api/alert-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name", notNullValue()));

            verifyNoInteractions(alertRuleService);
        }

        @Test
        @DisplayName("should update alert rule to disabled status")
        void shouldUpdateAlertRuleToDisabled() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = AlertRuleRequest.builder()
                    .name("disabled-alert-rule")
                    .thresholdPercentage(80)
                    .enabled(false)
                    .build();
            AlertRuleResponse response = AlertRuleResponse.builder()
                    .id(id)
                    .name("disabled-alert-rule")
                    .thresholdPercentage(80)
                    .windowSeconds(60)
                    .cooldownSeconds(300)
                    .enabled(false)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(alertRuleService.update(eq(id), any(AlertRuleRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/alert-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled", is(false)));

            verify(alertRuleService).update(eq(id), any(AlertRuleRequest.class));
        }

        @Test
        @DisplayName("should update threshold percentage to boundary value 1")
        void shouldUpdateThresholdPercentageToBoundary1() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = createValidAlertRuleRequest("low-threshold-rule", 1);
            AlertRuleResponse response = createAlertRuleResponse(id, "low-threshold-rule", 1);

            when(alertRuleService.update(eq(id), any(AlertRuleRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/alert-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.thresholdPercentage", is(1)));

            verify(alertRuleService).update(eq(id), any(AlertRuleRequest.class));
        }

        @Test
        @DisplayName("should update threshold percentage to boundary value 100")
        void shouldUpdateThresholdPercentageToBoundary100() throws Exception {
            UUID id = UUID.randomUUID();
            AlertRuleRequest request = createValidAlertRuleRequest("high-threshold-rule", 100);
            AlertRuleResponse response = createAlertRuleResponse(id, "high-threshold-rule", 100);

            when(alertRuleService.update(eq(id), any(AlertRuleRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/alert-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.thresholdPercentage", is(100)));

            verify(alertRuleService).update(eq(id), any(AlertRuleRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/alert-rules/{id}")
    class DeleteTests {

        @Test
        @DisplayName("should delete alert rule when exists")
        void shouldDeleteAlertRuleWhenExists() throws Exception {
            UUID id = UUID.randomUUID();

            doNothing().when(alertRuleService).delete(id);

            mockMvc.perform(delete("/api/alert-rules/{id}", id))
                    .andExpect(status().isNoContent());

            verify(alertRuleService).delete(id);
        }

        @Test
        @DisplayName("should return 404 when alert rule not found")
        void shouldReturn404WhenAlertRuleNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new EntityNotFoundException("Alert rule not found with id: " + id))
                    .when(alertRuleService).delete(id);

            mockMvc.perform(delete("/api/alert-rules/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));

            verify(alertRuleService).delete(id);
        }

        @Test
        @DisplayName("should return 400 when id is not a valid UUID")
        void shouldReturn400WhenIdInvalid() throws Exception {
            mockMvc.perform(delete("/api/alert-rules/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Parameter")));

            verifyNoInteractions(alertRuleService);
        }
    }
}
