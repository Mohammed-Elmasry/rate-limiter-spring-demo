package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.PolicyRuleRequest;
import com.example.ratelimiter.application.dto.PolicyRuleResponse;
import com.example.ratelimiter.application.mapper.PolicyRuleMapper;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.PolicyRule;
import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.PolicyScope;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.PolicyRuleRepository;
import com.example.ratelimiter.infrastructure.resilience.FallbackHandler;
import com.example.ratelimiter.infrastructure.util.PathPatternMatcher;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyRuleService Tests")
class PolicyRuleServiceTest {

    @Mock
    private PolicyRuleRepository policyRuleRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyRuleMapper policyRuleMapper;

    @Mock
    private PathPatternMatcher pathPatternMatcher;

    @Mock
    private FallbackHandler fallbackHandler;

    @InjectMocks
    private PolicyRuleService policyRuleService;

    private UUID testPolicyId;
    private UUID testRuleId;
    private Policy testPolicy;
    private PolicyRule testPolicyRule;
    private PolicyRuleRequest testRequest;
    private PolicyRuleResponse testResponse;

    @BeforeEach
    void setUp() {
        testPolicyId = UUID.randomUUID();
        testRuleId = UUID.randomUUID();

        testPolicy = Policy.builder()
                .id(testPolicyId)
                .name("Test Policy")
                .scope(PolicyScope.GLOBAL)
                .algorithm(Algorithm.TOKEN_BUCKET)
                .maxRequests(100)
                .windowSeconds(60)
                .enabled(true)
                .build();

        testPolicyRule = PolicyRule.builder()
                .id(testRuleId)
                .policy(testPolicy)
                .name("API Users Rule")
                .resourcePattern("/api/users/**")
                .httpMethods("GET,POST")
                .priority(10)
                .enabled(true)
                .build();

        testRequest = PolicyRuleRequest.builder()
                .policyId(testPolicyId)
                .name("API Users Rule")
                .resourcePattern("/api/users/**")
                .httpMethods("GET,POST")
                .priority(10)
                .enabled(true)
                .build();

        testResponse = PolicyRuleResponse.builder()
                .id(testRuleId)
                .policyId(testPolicyId)
                .policyName("Test Policy")
                .name("API Users Rule")
                .resourcePattern("/api/users/**")
                .httpMethods("GET,POST")
                .priority(10)
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("Create Policy Rule")
    class CreatePolicyRule {

        @Test
        @DisplayName("Should create policy rule successfully")
        void shouldCreatePolicyRuleSuccessfully() {
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(pathPatternMatcher.isValidPattern("/api/users/**")).thenReturn(true);
            when(policyRuleRepository.existsByNameAndPolicyId("API Users Rule", testPolicyId)).thenReturn(false);
            when(policyRuleMapper.toEntity(testRequest, testPolicy)).thenReturn(testPolicyRule);
            when(policyRuleRepository.save(testPolicyRule)).thenReturn(testPolicyRule);
            when(policyRuleMapper.toResponse(testPolicyRule)).thenReturn(testResponse);

            PolicyRuleResponse result = policyRuleService.create(testRequest);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("API Users Rule");
            assertThat(result.resourcePattern()).isEqualTo("/api/users/**");
            assertThat(result.httpMethods()).isEqualTo("GET,POST");
            assertThat(result.priority()).isEqualTo(10);

            verify(policyRuleRepository).save(testPolicyRule);
        }

        @Test
        @DisplayName("Should throw exception when policy not found")
        void shouldThrowExceptionWhenPolicyNotFound() {
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> policyRuleService.create(testRequest))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Policy not found");
        }

        @Test
        @DisplayName("Should throw exception for invalid pattern")
        void shouldThrowExceptionForInvalidPattern() {
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(pathPatternMatcher.isValidPattern(anyString())).thenReturn(false);

            assertThatThrownBy(() -> policyRuleService.create(testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid resource pattern");
        }

        @Test
        @DisplayName("Should throw exception for duplicate name")
        void shouldThrowExceptionForDuplicateName() {
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(pathPatternMatcher.isValidPattern(anyString())).thenReturn(true);
            when(policyRuleRepository.existsByNameAndPolicyId("API Users Rule", testPolicyId)).thenReturn(true);

            assertThatThrownBy(() -> policyRuleService.create(testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("Find Policy Rule")
    class FindPolicyRule {

        @Test
        @DisplayName("Should find all policy rules")
        void shouldFindAllPolicyRules() {
            when(policyRuleRepository.findAll()).thenReturn(List.of(testPolicyRule));
            when(policyRuleMapper.toResponse(testPolicyRule)).thenReturn(testResponse);

            List<PolicyRuleResponse> results = policyRuleService.findAll();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).name()).isEqualTo("API Users Rule");
        }

        @Test
        @DisplayName("Should find policy rule by id")
        void shouldFindPolicyRuleById() {
            when(policyRuleRepository.findById(testRuleId)).thenReturn(Optional.of(testPolicyRule));
            when(policyRuleMapper.toResponse(testPolicyRule)).thenReturn(testResponse);

            PolicyRuleResponse result = policyRuleService.findById(testRuleId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(testRuleId);
        }

        @Test
        @DisplayName("Should throw exception when rule not found by id")
        void shouldThrowExceptionWhenRuleNotFoundById() {
            when(policyRuleRepository.findById(testRuleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> policyRuleService.findById(testRuleId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Policy rule not found");
        }

        @Test
        @DisplayName("Should find rules by policy id")
        void shouldFindRulesByPolicyId() {
            when(policyRuleRepository.findByPolicyId(testPolicyId)).thenReturn(List.of(testPolicyRule));
            when(policyRuleMapper.toResponse(testPolicyRule)).thenReturn(testResponse);

            List<PolicyRuleResponse> results = policyRuleService.findByPolicyId(testPolicyId);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).policyId()).isEqualTo(testPolicyId);
        }
    }

    @Nested
    @DisplayName("Pattern Matching")
    class PatternMatching {

        @Test
        @DisplayName("Should find matching rule for exact path and method")
        void shouldFindMatchingRuleForExactPathAndMethod() {
            when(policyRuleRepository.findAllEnabledOrderedByPriority()).thenReturn(List.of(testPolicyRule));
            when(pathPatternMatcher.matches("/api/users/**", "/api/users/123")).thenReturn(true);
            when(policyRuleMapper.toResponse(testPolicyRule)).thenReturn(testResponse);

            Optional<PolicyRuleResponse> result = policyRuleService.findMatchingRule("/api/users/123", "GET");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("API Users Rule");
        }

        @Test
        @DisplayName("Should not match when path does not match pattern")
        void shouldNotMatchWhenPathDoesNotMatchPattern() {
            when(policyRuleRepository.findAllEnabledOrderedByPriority()).thenReturn(List.of(testPolicyRule));
            when(pathPatternMatcher.matches("/api/users/**", "/api/orders/123")).thenReturn(false);

            Optional<PolicyRuleResponse> result = policyRuleService.findMatchingRule("/api/orders/123", "GET");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when resource path is null")
        void shouldReturnEmptyWhenResourcePathIsNull() {
            Optional<PolicyRuleResponse> result = policyRuleService.findMatchingRule(null, "GET");

            assertThat(result).isEmpty();
            verify(policyRuleRepository, never()).findAllEnabledOrderedByPriority();
        }

        @Test
        @DisplayName("Should return first matching rule based on priority")
        void shouldReturnFirstMatchingRuleBasedOnPriority() {
            PolicyRule highPriorityRule = PolicyRule.builder()
                    .id(UUID.randomUUID())
                    .policy(testPolicy)
                    .name("High Priority Rule")
                    .resourcePattern("/api/**")
                    .priority(20)
                    .enabled(true)
                    .build();

            PolicyRule lowPriorityRule = PolicyRule.builder()
                    .id(UUID.randomUUID())
                    .policy(testPolicy)
                    .name("Low Priority Rule")
                    .resourcePattern("/api/users/**")
                    .priority(5)
                    .enabled(true)
                    .build();

            when(policyRuleRepository.findAllEnabledOrderedByPriority())
                    .thenReturn(List.of(highPriorityRule, lowPriorityRule));
            when(pathPatternMatcher.matches("/api/**", "/api/users/123")).thenReturn(true);
            when(policyRuleMapper.toResponse(highPriorityRule))
                    .thenReturn(PolicyRuleResponse.builder()
                            .id(highPriorityRule.getId())
                            .name("High Priority Rule")
                            .priority(20)
                            .build());

            Optional<PolicyRuleResponse> result = policyRuleService.findMatchingRule("/api/users/123", "GET");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("High Priority Rule");
            assertThat(result.get().priority()).isEqualTo(20);
        }

        @Test
        @DisplayName("Should find policy for matching resource")
        void shouldFindPolicyForMatchingResource() {
            when(policyRuleRepository.findAllEnabledOrderedByPriority()).thenReturn(List.of(testPolicyRule));
            when(pathPatternMatcher.matches("/api/users/**", "/api/users/123")).thenReturn(true);
            when(policyRuleMapper.toResponse(testPolicyRule)).thenReturn(testResponse);
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));

            Optional<Policy> result = policyRuleService.findPolicyForResource("/api/users/123", "GET");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(testPolicyId);
        }

        @Test
        @DisplayName("Should return empty when no rule matches")
        void shouldReturnEmptyWhenNoRuleMatches() {
            when(policyRuleRepository.findAllEnabledOrderedByPriority()).thenReturn(List.of(testPolicyRule));
            when(pathPatternMatcher.matches("/api/users/**", "/api/orders/123")).thenReturn(false);

            Optional<Policy> result = policyRuleService.findPolicyForResource("/api/orders/123", "GET");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Update Policy Rule")
    class UpdatePolicyRule {

        @Test
        @DisplayName("Should update policy rule successfully")
        void shouldUpdatePolicyRuleSuccessfully() {
            when(policyRuleRepository.findById(testRuleId)).thenReturn(Optional.of(testPolicyRule));
            when(policyRepository.findById(testPolicyId)).thenReturn(Optional.of(testPolicy));
            when(pathPatternMatcher.isValidPattern(anyString())).thenReturn(true);
            when(policyRuleRepository.findAll()).thenReturn(List.of(testPolicyRule));
            when(policyRuleRepository.save(testPolicyRule)).thenReturn(testPolicyRule);
            when(policyRuleMapper.toResponse(testPolicyRule)).thenReturn(testResponse);

            PolicyRuleResponse result = policyRuleService.update(testRuleId, testRequest);

            assertThat(result).isNotNull();
            verify(policyRuleMapper).updateEntity(testPolicyRule, testRequest, testPolicy);
            verify(policyRuleRepository).save(testPolicyRule);
        }

        @Test
        @DisplayName("Should throw exception when rule not found for update")
        void shouldThrowExceptionWhenRuleNotFoundForUpdate() {
            when(policyRuleRepository.findById(testRuleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> policyRuleService.update(testRuleId, testRequest))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Policy rule not found");
        }
    }

    @Nested
    @DisplayName("Delete Policy Rule")
    class DeletePolicyRule {

        @Test
        @DisplayName("Should delete policy rule successfully")
        void shouldDeletePolicyRuleSuccessfully() {
            when(policyRuleRepository.existsById(testRuleId)).thenReturn(true);

            policyRuleService.delete(testRuleId);

            verify(policyRuleRepository).deleteById(testRuleId);
        }

        @Test
        @DisplayName("Should throw exception when rule not found for delete")
        void shouldThrowExceptionWhenRuleNotFoundForDelete() {
            when(policyRuleRepository.existsById(testRuleId)).thenReturn(false);

            assertThatThrownBy(() -> policyRuleService.delete(testRuleId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Policy rule not found");
        }
    }
}
