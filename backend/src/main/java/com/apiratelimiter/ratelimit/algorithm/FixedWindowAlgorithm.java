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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(FixedWindowAlgorithm.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> fixedWindowScript;

    // Fallback local memory for testing when Redis is disabled
    private final ConcurrentHashMap<String, AtomicLong> localWindows = new ConcurrentHashMap<>();

    public FixedWindowAlgorithm(
        StringRedisTemplate redisTemplate,
        @Qualifier("fixedWindowScript") RedisScript<List<Long>> fixedWindowScript
    ) {
        this.redisTemplate = redisTemplate;
        this.fixedWindowScript = fixedWindowScript;
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.FIXED_WINDOW;
    }

    @Override
    public RateLimitResult evaluate(String key, RateLimitRule rule, long cost) {
        long nowMs = Instant.now().toEpochMilli();
        long windowSeconds = Math.max(1, rule.windowSeconds());
        long windowBucket = nowMs / (windowSeconds * 1000L);
        String redisKey = "rl:fw:" + key + ":" + windowBucket;

        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                List<Long> result = redisTemplate.execute(
                    fixedWindowScript,
                    List.of(redisKey),
                    String.valueOf(rule.limit()),
                    String.valueOf(windowSeconds),
                    String.valueOf(cost),
                    String.valueOf(nowMs)
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
            log.warn("Redis error during Fixed Window evaluation for key {}: {}. Using in-memory fallback.", key, e.getMessage());
        }

        // In-memory fallback
        AtomicLong counter = localWindows.computeIfAbsent(redisKey, k -> new AtomicLong(0));
        long current = counter.addAndGet(cost);
        long timeInWindow = (nowMs % (windowSeconds * 1000L)) / 1000L;
        long resetSeconds = windowSeconds - timeInWindow;

        if (current <= rule.limit()) {
            return RateLimitResult.allow(rule.limit(), rule.limit() - current, resetSeconds, getType().name(), key);
        } else {
            return RateLimitResult.block(rule.limit(), 0, resetSeconds, resetSeconds, getType().name(), key);
        }
    }
}
