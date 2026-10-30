package com.apiratelimiter.ratelimit.algorithm;

import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LeakyBucketAlgorithmTest {

    private LeakyBucketAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new LeakyBucketAlgorithm(null, null);
    }

    @Test
    @DisplayName("Leaky Bucket accepts requests until capacity overflows")
    void testLeakyBucketCapacityAndOverflow() {
        // capacity = 3, leakRate = 1/sec
        RateLimitRule rule = new RateLimitRule("lb_client", AlgorithmType.LEAKY_BUCKET, 3L, 3L, 3L, 1.0);

        assertTrue(algorithm.evaluate("lb_key_1", rule, 1).allowed());
        assertTrue(algorithm.evaluate("lb_key_1", rule, 1).allowed());
        assertTrue(algorithm.evaluate("lb_key_1", rule, 1).allowed());

        // 4th burst request overflows the bucket
        RateLimitResult overflow = algorithm.evaluate("lb_key_1", rule, 1);
        assertFalse(overflow.allowed(), "Bucket should overflow");
        assertTrue(overflow.retryAfterSeconds() >= 1);
    }
}
