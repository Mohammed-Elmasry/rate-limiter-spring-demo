package com.example.ratelimiter.infrastructure.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PathPatternMatcher Tests")
class PathPatternMatcherTest {

    private PathPatternMatcher pathPatternMatcher;

    @BeforeEach
    void setUp() {
        pathPatternMatcher = new PathPatternMatcher();
    }

    @Nested
    @DisplayName("Pattern Matching")
    class PatternMatching {

        @Test
        @DisplayName("Should match exact path")
        void shouldMatchExactPath() {
            assertThat(pathPatternMatcher.matches("/api/users", "/api/users")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/users", "/api/orders")).isFalse();
        }

        @Test
        @DisplayName("Should match single wildcard")
        void shouldMatchSingleWildcard() {
            assertThat(pathPatternMatcher.matches("/api/users/*", "/api/users/123")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/users/*", "/api/users/123/profile")).isFalse();
        }

        @Test
        @DisplayName("Should match double wildcard")
        void shouldMatchDoubleWildcard() {
            assertThat(pathPatternMatcher.matches("/api/**", "/api/users")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/**", "/api/users/123")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/**", "/api/users/123/profile")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/**", "/other/path")).isFalse();
        }

        @Test
        @DisplayName("Should match path variables")
        void shouldMatchPathVariables() {
            assertThat(pathPatternMatcher.matches("/users/{id}", "/users/123")).isTrue();
            assertThat(pathPatternMatcher.matches("/users/{id}", "/users/abc")).isTrue();
            assertThat(pathPatternMatcher.matches("/users/{id}/orders/{orderId}", "/users/123/orders/456")).isTrue();
        }

        @Test
        @DisplayName("Should match complex patterns")
        void shouldMatchComplexPatterns() {
            assertThat(pathPatternMatcher.matches("/api/v1/**", "/api/v1/users")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/v1/**", "/api/v1/users/123/profile")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/v*/users", "/api/v1/users")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/v*/users", "/api/v2/users")).isTrue();
        }

        @Test
        @DisplayName("Should handle null pattern or path")
        void shouldHandleNullPatternOrPath() {
            assertThat(pathPatternMatcher.matches(null, "/api/users")).isFalse();
            assertThat(pathPatternMatcher.matches("/api/users", null)).isFalse();
            assertThat(pathPatternMatcher.matches(null, null)).isFalse();
        }

        @Test
        @DisplayName("Should be case sensitive")
        void shouldBeCaseSensitive() {
            assertThat(pathPatternMatcher.matches("/api/Users", "/api/Users")).isTrue();
            assertThat(pathPatternMatcher.matches("/api/users", "/api/Users")).isFalse();
            assertThat(pathPatternMatcher.matches("/API/users", "/api/users")).isFalse();
        }
    }

    @Nested
    @DisplayName("Pattern Validation")
    class PatternValidation {

        @Test
        @DisplayName("Should validate valid patterns")
        void shouldValidateValidPatterns() {
            assertThat(pathPatternMatcher.isValidPattern("/api/users")).isTrue();
            assertThat(pathPatternMatcher.isValidPattern("/api/**")).isTrue();
            assertThat(pathPatternMatcher.isValidPattern("/users/*")).isTrue();
            assertThat(pathPatternMatcher.isValidPattern("/users/{id}")).isTrue();
            assertThat(pathPatternMatcher.isValidPattern("/api/v1/users/**")).isTrue();
        }

        @Test
        @DisplayName("Should reject invalid patterns")
        void shouldRejectInvalidPatterns() {
            assertThat(pathPatternMatcher.isValidPattern(null)).isFalse();
            assertThat(pathPatternMatcher.isValidPattern("")).isFalse();
            assertThat(pathPatternMatcher.isValidPattern("   ")).isFalse();
            assertThat(pathPatternMatcher.isValidPattern("api/users")).isFalse(); // Must start with /
        }

        @Test
        @DisplayName("Should reject patterns with too many slashes")
        void shouldRejectPatternsWithTooManySlashes() {
            assertThat(pathPatternMatcher.isValidPattern("/api///users")).isFalse();
        }
    }

    @Nested
    @DisplayName("URI Template Variables")
    class UriTemplateVariables {

        @Test
        @DisplayName("Should extract single path variable")
        void shouldExtractSinglePathVariable() {
            Map<String, String> variables = pathPatternMatcher.extractUriTemplateVariables(
                    "/users/{id}", "/users/123");

            assertThat(variables).hasSize(1);
            assertThat(variables.get("id")).isEqualTo("123");
        }

        @Test
        @DisplayName("Should extract multiple path variables")
        void shouldExtractMultiplePathVariables() {
            Map<String, String> variables = pathPatternMatcher.extractUriTemplateVariables(
                    "/users/{userId}/orders/{orderId}", "/users/123/orders/456");

            assertThat(variables).hasSize(2);
            assertThat(variables.get("userId")).isEqualTo("123");
            assertThat(variables.get("orderId")).isEqualTo("456");
        }

        @Test
        @DisplayName("Should return empty map when pattern does not match")
        void shouldReturnEmptyMapWhenPatternDoesNotMatch() {
            Map<String, String> variables = pathPatternMatcher.extractUriTemplateVariables(
                    "/users/{id}", "/orders/123");

            assertThat(variables).isEmpty();
        }

        @Test
        @DisplayName("Should handle null pattern or path")
        void shouldHandleNullPatternOrPathForExtraction() {
            assertThat(pathPatternMatcher.extractUriTemplateVariables(null, "/users/123")).isEmpty();
            assertThat(pathPatternMatcher.extractUriTemplateVariables("/users/{id}", null)).isEmpty();
        }

        @Test
        @DisplayName("Should extract variables from complex patterns")
        void shouldExtractVariablesFromComplexPatterns() {
            Map<String, String> variables = pathPatternMatcher.extractUriTemplateVariables(
                    "/api/v{version}/users/{userId}/profile", "/api/v1/users/123/profile");

            assertThat(variables).hasSize(2);
            assertThat(variables.get("version")).isEqualTo("1");
            assertThat(variables.get("userId")).isEqualTo("123");
        }
    }
}
