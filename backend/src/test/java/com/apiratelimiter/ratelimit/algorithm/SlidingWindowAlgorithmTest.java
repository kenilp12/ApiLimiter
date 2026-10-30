package com.apiratelimiter.ratelimit.algorithm;

import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowAlgorithmTest {

    private SlidingWindowAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new SlidingWindowAlgorithm(null, null);
    }

    @Test
    @DisplayName("Sliding Window allows requests up to limit and calculates remaining")
    void testSlidingWindowAllow() {
        RateLimitRule rule = new RateLimitRule("sw_client", AlgorithmType.SLIDING_WINDOW, 4L, 10L, 4L, 0.0);

        for (int i = 1; i <= 4; i++) {
            RateLimitResult result = algorithm.evaluate("sw_key_1", rule, 1);
            assertTrue(result.allowed(), "Request " + i + " must be allowed");
            assertEquals(4 - i, result.remaining());
        }

        RateLimitResult throttled = algorithm.evaluate("sw_key_1", rule, 1);
        assertFalse(throttled.allowed(), "Request 5 must be throttled");
        assertEquals(0, throttled.remaining());
        assertTrue(throttled.retryAfterSeconds() >= 1);
    }
}
