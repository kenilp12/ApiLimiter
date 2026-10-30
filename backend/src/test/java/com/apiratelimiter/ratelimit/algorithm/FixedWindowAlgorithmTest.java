package com.apiratelimiter.ratelimit.algorithm;

import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowAlgorithmTest {

    private FixedWindowAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new FixedWindowAlgorithm(null, null);
    }

    @Test
    @DisplayName("Fixed Window should allow requests within limit")
    void testAllowedWithinLimit() {
        RateLimitRule rule = new RateLimitRule("test_client", AlgorithmType.FIXED_WINDOW, 5L, 60L, 5L, 0.0);

        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = algorithm.evaluate("client_1", rule, 1);
            assertTrue(result.allowed(), "Request " + i + " should be allowed");
            assertEquals(5 - i, result.remaining());
        }
    }

    @Test
    @DisplayName("Fixed Window should block requests exceeding limit")
    void testBlockExceedingLimit() {
        RateLimitRule rule = new RateLimitRule("test_client_2", AlgorithmType.FIXED_WINDOW, 3L, 60L, 3L, 0.0);

        algorithm.evaluate("client_2", rule, 1);
        algorithm.evaluate("client_2", rule, 1);
        algorithm.evaluate("client_2", rule, 1);

        RateLimitResult blocked = algorithm.evaluate("client_2", rule, 1);
        assertFalse(blocked.allowed(), "4th request must be blocked");
        assertEquals(0, blocked.remaining());
        assertTrue(blocked.retryAfterSeconds() > 0);
    }
}
