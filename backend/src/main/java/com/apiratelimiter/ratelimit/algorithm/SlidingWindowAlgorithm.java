package com.apiratelimiter.ratelimit.algorithm;

import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlidingWindowAlgorithm implements RateLimitAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowAlgorithm.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> slidingWindowScript;

    // Fallback local memory for testing when Redis is disabled
    private final ConcurrentHashMap<String, Deque<Long>> localLogs = new ConcurrentHashMap<>();

    public SlidingWindowAlgorithm(
        StringRedisTemplate redisTemplate,
        @Qualifier("slidingWindowScript") RedisScript<List<Long>> slidingWindowScript
    ) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = slidingWindowScript;
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.SLIDING_WINDOW;
    }

    @Override
    public RateLimitResult evaluate(String key, RateLimitRule rule, long cost) {
        long nowMs = Instant.now().toEpochMilli();
        long windowMs = Math.max(1, rule.windowSeconds()) * 1000L;
        String redisKey = "rl:sw:" + key;
        String requestId = UUID.randomUUID().toString();

        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                List<Long> result = redisTemplate.execute(
                    slidingWindowScript,
                    List.of(redisKey),
                    String.valueOf(rule.limit()),
                    String.valueOf(windowMs),
                    String.valueOf(cost),
                    String.valueOf(nowMs),
                    requestId
                );

                if (result != null && result.size() >= 4) {
                    boolean allowed = result.get(0) == 1L;
                    long remaining = result.get(1);
                    long resetSeconds = result.get(2);
                    long retryAfterSeconds = result.get(3);

                    return new RateLimitResult(allowed, rule.limit(), remaining, resetSeconds, retryAfterSeconds, getType().name(), key);
                }
            }
        } catch (Exception e) {
            log.warn("Redis error during Sliding Window evaluation for key {}: {}. Using in-memory fallback.", key, e.getMessage());
        }

        // In-memory fallback
        Deque<Long> timestamps = localLogs.computeIfAbsent(redisKey, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            long windowStart = nowMs - windowMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() + cost <= rule.limit()) {
                for (int i = 0; i < cost; i++) {
                    timestamps.addLast(nowMs);
                }
                long remaining = rule.limit() - timestamps.size();
                long resetSeconds = Math.max(1, (windowMs / 1000L));
                return RateLimitResult.allow(rule.limit(), remaining, resetSeconds, getType().name(), key);
            } else {
                long oldest = timestamps.isEmpty() ? nowMs : timestamps.peekFirst();
                long waitMs = Math.max(1000L, (oldest + windowMs) - nowMs);
                long retryAfterSeconds = (long) Math.ceil(waitMs / 1000.0);
                return RateLimitResult.block(rule.limit(), 0, retryAfterSeconds, retryAfterSeconds, getType().name(), key);
            }
        }
    }
}
