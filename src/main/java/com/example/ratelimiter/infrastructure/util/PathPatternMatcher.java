package com.example.ratelimiter.infrastructure.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Utility class for matching URL paths against Ant-style patterns.
 * Supports patterns like:
 * - /api/** (matches /api/users, /api/v1/users, etc.)
 * - /users/* (matches /users/123 but not /users/123/profile)
 * - /orders/{id} (matches /orders/123, /orders/abc, etc.)
 * - /exact/path (exact match)
 *
 * This class wraps Spring's AntPathMatcher for consistency and extensibility.
 */
@Component
@Slf4j
public class PathPatternMatcher {

    private final AntPathMatcher pathMatcher;

    public PathPatternMatcher() {
        this.pathMatcher = new AntPathMatcher();
        // Set path separator for URL matching
        this.pathMatcher.setPathSeparator("/");
        // Case-sensitive matching for URLs
        this.pathMatcher.setCaseSensitive(true);
    }

    /**
     * Checks if the given path matches the pattern.
     *
     * @param pattern The Ant-style pattern (e.g., /api/**, /users/*)
     * @param path The actual path to match (e.g., /api/v1/users)
     * @return true if the path matches the pattern, false otherwise
     */
    public boolean matches(String pattern, String path) {
        if (pattern == null || path == null) {
            log.warn("Pattern or path is null - pattern: {}, path: {}", pattern, path);
            return false;
        }

        try {
            boolean result = pathMatcher.match(pattern, path);
            log.debug("Pattern match: pattern={}, path={}, result={}", pattern, path, result);
            return result;
        } catch (Exception e) {
            log.error("Error matching pattern '{}' against path '{}': {}", pattern, path, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the pattern is a valid Ant-style pattern.
     * This is a basic validation that checks for common mistakes.
     *
     * @param pattern The pattern to validate
     * @return true if the pattern is valid, false otherwise
     */
    public boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return false;
        }

        // Pattern should start with /
        if (!pattern.startsWith("/")) {
            return false;
        }

        // Check for consecutive slashes (except //)
        if (pattern.contains("///")) {
            return false;
        }

        return true;
    }

    /**
     * Extracts path variables from the pattern and path.
     * For example, pattern=/users/{id} and path=/users/123 returns {"id": "123"}
     *
     * @param pattern The pattern with variables (e.g., /users/{id})
     * @param path The actual path (e.g., /users/123)
     * @return Map of variable names to values, or empty map if no match
     */
    public java.util.Map<String, String> extractUriTemplateVariables(String pattern, String path) {
        if (pattern == null || path == null) {
            return java.util.Collections.emptyMap();
        }

        try {
            if (pathMatcher.match(pattern, path)) {
                return pathMatcher.extractUriTemplateVariables(pattern, path);
            }
        } catch (Exception e) {
            log.error("Error extracting variables from pattern '{}' and path '{}': {}",
                pattern, path, e.getMessage());
        }

        return java.util.Collections.emptyMap();
    }
}
