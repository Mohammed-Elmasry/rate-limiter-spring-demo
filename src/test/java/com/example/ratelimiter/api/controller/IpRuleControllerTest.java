package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.api.exception.GlobalExceptionHandler;
import com.example.ratelimiter.application.dto.IpRuleRequest;
import com.example.ratelimiter.application.dto.IpRuleResponse;
import com.example.ratelimiter.application.service.IpRuleService;
import com.example.ratelimiter.domain.enums.RuleType;
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
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {IpRuleController.class, GlobalExceptionHandler.class})
class IpRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IpRuleService ipRuleService;

    private IpRuleResponse createIpRuleResponse(UUID id, String ipAddress, String ipCidr) {
        return IpRuleResponse.builder()
                .id(id)
                .ipAddress(ipAddress)
                .ipCidr(ipCidr)
                .ruleType(RuleType.RATE_LIMIT)
                .policyId(UUID.randomUUID())
                .policyName("Test Policy")
                .tenantId(UUID.randomUUID())
                .description("Test IP rule")
                .enabled(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private IpRuleRequest createValidIpRuleRequest(String ipAddress, String ipCidr, UUID policyId) {
        return IpRuleRequest.builder()
                .ipAddress(ipAddress)
                .ipCidr(ipCidr)
                .ruleType(RuleType.RATE_LIMIT)
                .policyId(policyId)
                .tenantId(UUID.randomUUID())
                .description("Test IP rule")
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("GET /api/ip-rules")
    class FindAllTests {

        @Test
        @DisplayName("should return empty list when no IP rules exist")
        void shouldReturnEmptyList() throws Exception {
            when(ipRuleService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/ip-rules"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(ipRuleService).findAll();
        }

        @Test
        @DisplayName("should return all IP rules")
        void shouldReturnAllIpRules() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<IpRuleResponse> ipRules = List.of(
                    createIpRuleResponse(id1, "192.168.1.1", null),
                    createIpRuleResponse(id2, null, "10.0.0.0/8")
            );

            when(ipRuleService.findAll()).thenReturn(ipRules);

            mockMvc.perform(get("/api/ip-rules"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(id1.toString())))
                    .andExpect(jsonPath("$[0].ipAddress", is("192.168.1.1")))
                    .andExpect(jsonPath("$[1].id", is(id2.toString())))
                    .andExpect(jsonPath("$[1].ipCidr", is("10.0.0.0/8")));

            verify(ipRuleService).findAll();
        }
    }

    @Nested
    @DisplayName("GET /api/ip-rules/{id}")
    class FindByIdTests {

        @Test
        @DisplayName("should return IP rule when exists")
        void shouldReturnIpRuleWhenExists() throws Exception {
            UUID id = UUID.randomUUID();
            IpRuleResponse ipRule = createIpRuleResponse(id, "192.168.1.100", null);

            when(ipRuleService.findById(id)).thenReturn(ipRule);

            mockMvc.perform(get("/api/ip-rules/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.ipAddress", is("192.168.1.100")))
                    .andExpect(jsonPath("$.ruleType", is("RATE_LIMIT")))
                    .andExpect(jsonPath("$.enabled", is(true)));

            verify(ipRuleService).findById(id);
        }

        @Test
        @DisplayName("should return 404 when IP rule not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            when(ipRuleService.findById(id))
                    .thenThrow(new EntityNotFoundException("IP rule not found with id: " + id));

            mockMvc.perform(get("/api/ip-rules/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(id.toString())));

            verify(ipRuleService).findById(id);
        }
    }

    @Nested
    @DisplayName("GET /api/ip-rules/tenant/{tenantId}")
    class FindByTenantIdTests {

        @Test
        @DisplayName("should return IP rules for tenant")
        void shouldReturnIpRulesForTenant() throws Exception {
            UUID tenantId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            IpRuleResponse ipRule = IpRuleResponse.builder()
                    .id(ruleId)
                    .ipAddress("192.168.1.1")
                    .ruleType(RuleType.RATE_LIMIT)
                    .policyId(UUID.randomUUID())
                    .tenantId(tenantId)
                    .enabled(true)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(ipRuleService.findByTenantId(tenantId)).thenReturn(List.of(ipRule));

            mockMvc.perform(get("/api/ip-rules/tenant/{tenantId}", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].tenantId", is(tenantId.toString())));

            verify(ipRuleService).findByTenantId(tenantId);
        }

        @Test
        @DisplayName("should return empty list when no rules for tenant")
        void shouldReturnEmptyListWhenNoRulesForTenant() throws Exception {
            UUID tenantId = UUID.randomUUID();

            when(ipRuleService.findByTenantId(tenantId)).thenReturn(List.of());

            mockMvc.perform(get("/api/ip-rules/tenant/{tenantId}", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(ipRuleService).findByTenantId(tenantId);
        }
    }

    @Nested
    @DisplayName("GET /api/ip-rules/rule-type/{ruleType}")
    class FindByRuleTypeTests {

        @Test
        @DisplayName("should return IP rules by rule type")
        void shouldReturnIpRulesByRuleType() throws Exception {
            UUID id = UUID.randomUUID();
            IpRuleResponse ipRule = createIpRuleResponse(id, "192.168.1.1", null);

            when(ipRuleService.findByRuleType("RATE_LIMIT")).thenReturn(List.of(ipRule));

            mockMvc.perform(get("/api/ip-rules/rule-type/{ruleType}", "RATE_LIMIT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].ruleType", is("RATE_LIMIT")));

            verify(ipRuleService).findByRuleType("RATE_LIMIT");
        }
    }

    @Nested
    @DisplayName("GET /api/ip-rules/match/{ip}")
    class FindMatchingRuleTests {

        @Test
        @DisplayName("should return matching IP rule when found")
        void shouldReturnMatchingRuleWhenFound() throws Exception {
            String ip = "192.168.1.100";
            UUID id = UUID.randomUUID();
            IpRuleResponse ipRule = createIpRuleResponse(id, ip, null);

            when(ipRuleService.findMatchingRateLimitRuleForIp(ip)).thenReturn(Optional.of(ipRule));

            mockMvc.perform(get("/api/ip-rules/match/{ip}", ip))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ipAddress", is(ip)));

            verify(ipRuleService).findMatchingRateLimitRuleForIp(ip);
        }

        @Test
        @DisplayName("should return 404 when no matching rule found")
        void shouldReturn404WhenNoMatchingRuleFound() throws Exception {
            String ip = "192.168.1.100";

            when(ipRuleService.findMatchingRateLimitRuleForIp(ip)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/ip-rules/match/{ip}", ip))
                    .andExpect(status().isNotFound());

            verify(ipRuleService).findMatchingRateLimitRuleForIp(ip);
        }
    }

    @Nested
    @DisplayName("GET /api/ip-rules/match/{ip}/tenant/{tenantId}")
    class FindMatchingRuleForTenantTests {

        @Test
        @DisplayName("should return matching IP rule for tenant when found")
        void shouldReturnMatchingRuleForTenantWhenFound() throws Exception {
            String ip = "192.168.1.100";
            UUID tenantId = UUID.randomUUID();
            UUID id = UUID.randomUUID();
            IpRuleResponse ipRule = IpRuleResponse.builder()
                    .id(id)
                    .ipAddress(ip)
                    .ruleType(RuleType.RATE_LIMIT)
                    .policyId(UUID.randomUUID())
                    .tenantId(tenantId)
                    .enabled(true)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(ipRuleService.findMatchingRateLimitRuleForIpAndTenant(ip, tenantId))
                    .thenReturn(Optional.of(ipRule));

            mockMvc.perform(get("/api/ip-rules/match/{ip}/tenant/{tenantId}", ip, tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ipAddress", is(ip)))
                    .andExpect(jsonPath("$.tenantId", is(tenantId.toString())));

            verify(ipRuleService).findMatchingRateLimitRuleForIpAndTenant(ip, tenantId);
        }

        @Test
        @DisplayName("should return 404 when no matching rule for tenant")
        void shouldReturn404WhenNoMatchingRuleForTenant() throws Exception {
            String ip = "192.168.1.100";
            UUID tenantId = UUID.randomUUID();

            when(ipRuleService.findMatchingRateLimitRuleForIpAndTenant(ip, tenantId))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/ip-rules/match/{ip}/tenant/{tenantId}", ip, tenantId))
                    .andExpect(status().isNotFound());

            verify(ipRuleService).findMatchingRateLimitRuleForIpAndTenant(ip, tenantId);
        }
    }

    @Nested
    @DisplayName("POST /api/ip-rules")
    class CreateTests {

        @Test
        @DisplayName("should create IP rule with IP address")
        void shouldCreateIpRuleWithIpAddress() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            String ipAddress = "192.168.1.100";
            IpRuleRequest request = createValidIpRuleRequest(ipAddress, null, policyId);
            IpRuleResponse response = createIpRuleResponse(id, ipAddress, null);

            when(ipRuleService.create(any(IpRuleRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.ipAddress", is(ipAddress)));

            verify(ipRuleService).create(any(IpRuleRequest.class));
        }

        @Test
        @DisplayName("should create IP rule with CIDR notation")
        void shouldCreateIpRuleWithCidr() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            String ipCidr = "10.0.0.0/8";
            IpRuleRequest request = createValidIpRuleRequest(null, ipCidr, policyId);
            IpRuleResponse response = createIpRuleResponse(id, null, ipCidr);

            when(ipRuleService.create(any(IpRuleRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.ipCidr", is(ipCidr)));

            verify(ipRuleService).create(any(IpRuleRequest.class));
        }

        @Test
        @DisplayName("should return 400 when IP address format is invalid")
        void shouldReturn400WhenIpAddressInvalid() throws Exception {
            UUID policyId = UUID.randomUUID();
            String requestJson = String.format("""
                {
                    "ipAddress": "999.999.999.999",
                    "ruleType": "RATE_LIMIT",
                    "policyId": "%s",
                    "enabled": true
                }
                """, policyId);

            mockMvc.perform(post("/api/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.ipAddress", notNullValue()));

            verifyNoInteractions(ipRuleService);
        }

        @Test
        @DisplayName("should return 400 when CIDR notation format is invalid")
        void shouldReturn400WhenCidrInvalid() throws Exception {
            UUID policyId = UUID.randomUUID();
            String requestJson = String.format("""
                {
                    "ipCidr": "10.0.0.0/99",
                    "ruleType": "RATE_LIMIT",
                    "policyId": "%s",
                    "enabled": true
                }
                """, policyId);

            mockMvc.perform(post("/api/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.ipCidr", notNullValue()));

            verifyNoInteractions(ipRuleService);
        }

        @Test
        @DisplayName("should return 400 when rule type is null")
        void shouldReturn400WhenRuleTypeNull() throws Exception {
            UUID policyId = UUID.randomUUID();
            String requestJson = String.format("""
                {
                    "ipAddress": "192.168.1.100",
                    "policyId": "%s",
                    "enabled": true
                }
                """, policyId);

            mockMvc.perform(post("/api/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.ruleType", notNullValue()));

            verifyNoInteractions(ipRuleService);
        }

        @Test
        @DisplayName("should return 400 when policy ID is null")
        void shouldReturn400WhenPolicyIdNull() throws Exception {
            String requestJson = """
                {
                    "ipAddress": "192.168.1.100",
                    "ruleType": "RATE_LIMIT",
                    "enabled": true
                }
                """;

            mockMvc.perform(post("/api/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.policyId", notNullValue()));

            verifyNoInteractions(ipRuleService);
        }

        @Test
        @DisplayName("should return 400 when description exceeds maximum length")
        void shouldReturn400WhenDescriptionTooLong() throws Exception {
            UUID policyId = UUID.randomUUID();
            String longDescription = "a".repeat(1001);
            IpRuleRequest request = IpRuleRequest.builder()
                    .ipAddress("192.168.1.100")
                    .ruleType(RuleType.RATE_LIMIT)
                    .policyId(policyId)
                    .description(longDescription)
                    .build();

            mockMvc.perform(post("/api/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.description", notNullValue()));

            verifyNoInteractions(ipRuleService);
        }

        @Test
        @DisplayName("should return 404 when policy not found")
        void shouldReturn404WhenPolicyNotFound() throws Exception {
            UUID policyId = UUID.randomUUID();
            IpRuleRequest request = createValidIpRuleRequest("192.168.1.100", null, policyId);

            when(ipRuleService.create(any(IpRuleRequest.class)))
                    .thenThrow(new EntityNotFoundException("Policy not found with id: " + policyId));

            mockMvc.perform(post("/api/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));
        }
    }

    @Nested
    @DisplayName("PUT /api/ip-rules/{id}")
    class UpdateTests {

        @Test
        @DisplayName("should update IP rule with valid request")
        void shouldUpdateIpRuleWithValidRequest() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            IpRuleRequest request = createValidIpRuleRequest("192.168.1.200", null, policyId);
            IpRuleResponse response = createIpRuleResponse(id, "192.168.1.200", null);

            when(ipRuleService.update(eq(id), any(IpRuleRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/ip-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.ipAddress", is("192.168.1.200")));

            verify(ipRuleService).update(eq(id), any(IpRuleRequest.class));
        }

        @Test
        @DisplayName("should return 404 when IP rule not found")
        void shouldReturn404WhenIpRuleNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            IpRuleRequest request = createValidIpRuleRequest("192.168.1.200", null, policyId);

            when(ipRuleService.update(eq(id), any(IpRuleRequest.class)))
                    .thenThrow(new EntityNotFoundException("IP rule not found with id: " + id));

            mockMvc.perform(put("/api/ip-rules/{id}", id)
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
            String requestJson = String.format("""
                {
                    "ipAddress": "invalid-ip",
                    "ruleType": "RATE_LIMIT",
                    "policyId": "%s",
                    "enabled": true
                }
                """, policyId);

            mockMvc.perform(put("/api/ip-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.ipAddress", notNullValue()));

            verifyNoInteractions(ipRuleService);
        }

        @Test
        @DisplayName("should update IP rule from IP address to CIDR")
        void shouldUpdateIpRuleFromIpToCidr() throws Exception {
            UUID id = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();
            IpRuleRequest request = createValidIpRuleRequest(null, "172.16.0.0/12", policyId);
            IpRuleResponse response = createIpRuleResponse(id, null, "172.16.0.0/12");

            when(ipRuleService.update(eq(id), any(IpRuleRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/ip-rules/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ipCidr", is("172.16.0.0/12")));

            verify(ipRuleService).update(eq(id), any(IpRuleRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/ip-rules/{id}")
    class DeleteTests {

        @Test
        @DisplayName("should delete IP rule when exists")
        void shouldDeleteIpRuleWhenExists() throws Exception {
            UUID id = UUID.randomUUID();

            doNothing().when(ipRuleService).delete(id);

            mockMvc.perform(delete("/api/ip-rules/{id}", id))
                    .andExpect(status().isNoContent());

            verify(ipRuleService).delete(id);
        }

        @Test
        @DisplayName("should return 404 when IP rule not found")
        void shouldReturn404WhenIpRuleNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new EntityNotFoundException("IP rule not found with id: " + id))
                    .when(ipRuleService).delete(id);

            mockMvc.perform(delete("/api/ip-rules/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource Not Found")));

            verify(ipRuleService).delete(id);
        }
    }
}
