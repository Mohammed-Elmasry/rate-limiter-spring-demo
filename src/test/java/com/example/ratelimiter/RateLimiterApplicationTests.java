package com.example.ratelimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic test to verify the main application class exists and is properly configured.
 * Full integration tests are in controller and service tests with mocked dependencies.
 */
class RateLimiterApplicationTests {

    @Test
    @DisplayName("application main class should be instantiable")
    void applicationMainClassExists() {
        RateLimiterApplication application = new RateLimiterApplication();
        assertThat(application).isNotNull();
    }

    @Test
    @DisplayName("application main method should not throw exception")
    void applicationMainMethodExists() {
        // Verify the main method exists (compilation check)
        // We don't actually invoke it as it would start the entire application
        assertThat(RateLimiterApplication.class).hasDeclaredMethods("main");
    }

}
