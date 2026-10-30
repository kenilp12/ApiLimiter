package com.apiratelimiter.ratelimit.algorithm;

import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketAlgorithmTest {

    private TokenBucketAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new TokenBucketAlgorithm(null, null);
    }

    @Test
    @DisplayName("Token Bucket consumes tokens and rejects when bucket is exhausted")
    void testTokenConsumptionAndExhaustion() {
        // capacity = 3, refillRate = 1 token/sec
        RateLimitRule rule = new RateLimitRule("tb_client", AlgorithmType.TOKEN_BUCKET, 3L, 3L, 3L, 1.0);

        assertTrue(algorithm.evaluate("tb_key_1", rule, 1).allowed());
        assertTrue(algorithm.evaluate("tb_key_1", rule, 1).allowed());
        assertTrue(algorithm.evaluate("tb_key_1", rule, 1).allowed());

        // 4th request without delay must be rejected
        RateLimitResult exhausted = algorithm.evaluate("tb_key_1", rule, 1);
        assertFalse(exhausted.allowed(), "Bucket should be exhausted");
        assertTrue(exhausted.retryAfterSeconds() >= 1);
    }
}
