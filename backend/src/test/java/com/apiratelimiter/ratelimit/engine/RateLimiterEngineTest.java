package com.apiratelimiter.ratelimit.engine;

import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.ratelimit.algorithm.FixedWindowAlgorithm;
import com.apiratelimiter.ratelimit.algorithm.SlidingWindowAlgorithm;
import com.apiratelimiter.ratelimit.algorithm.TokenBucketAlgorithm;
import com.apiratelimiter.ratelimit.algorithm.LeakyBucketAlgorithm;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterEngineTest {

    private RateLimiterEngine engine;

    @BeforeEach
    void setUp() {
        FixedWindowAlgorithm fw = new FixedWindowAlgorithm(null, null);
        SlidingWindowAlgorithm sw = new SlidingWindowAlgorithm(null, null);
        TokenBucketAlgorithm tb = new TokenBucketAlgorithm(null, null);
        LeakyBucketAlgorithm lb = new LeakyBucketAlgorithm(null, null);

        engine = new RateLimiterEngine(List.of(fw, sw, tb, lb), false);
    }

    @Test
    @DisplayName("Engine dispatches to the correct algorithm")
    void testEngineDispatch() {
        RateLimitRule rule = new RateLimitRule("client_engine", AlgorithmType.TOKEN_BUCKET, 10L, 60L, 10L, 1.0);
        RateLimitResult result = engine.checkRateLimit("client_engine", rule, 1);

        assertTrue(result.allowed());
        assertEquals("TOKEN_BUCKET", result.algorithm());
    }

    @Test
    @DisplayName("Engine enforces rate limit under concurrent request pressure")
    void testConcurrencyEnforcement() throws InterruptedException {
        int limit = 15;
        int threads = 30;
        RateLimitRule rule = new RateLimitRule("concurrent_key", AlgorithmType.FIXED_WINDOW, (long) limit, 60L, (long) limit, 0.0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult res = engine.checkRateLimit("concurrent_key", rule, 1);
                    if (res.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        blockedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(limit, allowedCount.get(), "Allowed count must equal exactly the limit");
        assertEquals(threads - limit, blockedCount.get(), "Excess requests must be blocked");
    }

    @Test
    @DisplayName("Engine respects fail-closed mode when unknown algorithm is requested")
    void testFailClosedOnUnknownAlgorithm() {
        RateLimiterEngine failClosedEngine = new RateLimiterEngine(List.of(), false);
        RateLimitRule rule = new RateLimitRule("fail_closed_key", AlgorithmType.TOKEN_BUCKET, 10L, 60L, 10L, 1.0);

        RateLimitResult res = failClosedEngine.checkRateLimit("fail_closed_key", rule, 1);
        assertFalse(res.allowed(), "Fail closed should deny traffic");
        assertEquals("FAIL_CLOSED", res.algorithm());
    }

    @Test
    @DisplayName("Engine respects fail-open mode when configured")
    void testFailOpenMode() {
        RateLimiterEngine failOpenEngine = new RateLimiterEngine(List.of(), true);
        RateLimitRule rule = new RateLimitRule("fail_open_key", AlgorithmType.TOKEN_BUCKET, 10L, 60L, 10L, 1.0);

        RateLimitResult res = failOpenEngine.checkRateLimit("fail_open_key", rule, 1);
        assertTrue(res.allowed(), "Fail open should permit traffic");
        assertEquals("FAIL_OPEN", res.algorithm());
    }
}
