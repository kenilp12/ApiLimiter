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

@Component
public class LeakyBucketAlgorithm implements RateLimitAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(LeakyBucketAlgorithm.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> leakyBucketScript;

    // Fallback local memory for testing when Redis is disabled
    private static class LocalLeakyBucket {
        double water;
        long lastLeakMs;
        LocalLeakyBucket(double water, long lastLeakMs) {
            this.water = water;
            this.lastLeakMs = lastLeakMs;
        }
    }
    private final ConcurrentHashMap<String, LocalLeakyBucket> localBuckets = new ConcurrentHashMap<>();

    public LeakyBucketAlgorithm(
        StringRedisTemplate redisTemplate,
        @Qualifier("leakyBucketScript") RedisScript<List<Long>> leakyBucketScript
    ) {
        this.redisTemplate = redisTemplate;
        this.leakyBucketScript = leakyBucketScript;
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.LEAKY_BUCKET;
    }

    @Override
    public RateLimitResult evaluate(String key, RateLimitRule rule, long cost) {
        long nowMs = Instant.now().toEpochMilli();
        long capacity = rule.burstCapacity() != null && rule.burstCapacity() > 0 ? rule.burstCapacity() : rule.limit();
        double leakRate = rule.refillRate() != null && rule.refillRate() > 0 
            ? rule.refillRate() 
            : (double) rule.limit() / Math.max(1, rule.windowSeconds());
        
        String redisKey = "rl:lb:" + key;

        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                List<Long> result = redisTemplate.execute(
                    leakyBucketScript,
                    List.of(redisKey),
                    String.valueOf(capacity),
                    String.valueOf(leakRate),
                    String.valueOf(cost),
                    String.valueOf(nowMs)
                );

                if (result != null && result.size() >= 4) {
                    boolean allowed = result.get(0) == 1L;
                    long remaining = result.get(1);
                    long resetSeconds = result.get(2);
                    long retryAfterSeconds = result.get(3);

                    return new RateLimitResult(allowed, capacity, remaining, resetSeconds, retryAfterSeconds, getType().name(), key);
                }
            }
        } catch (Exception e) {
            log.warn("Redis error during Leaky Bucket evaluation for key {}: {}. Using in-memory fallback.", key, e.getMessage());
        }

        // In-memory fallback
        LocalLeakyBucket bucket = localBuckets.computeIfAbsent(redisKey, k -> new LocalLeakyBucket(0.0, nowMs));
        synchronized (bucket) {
            long deltaMs = Math.max(0, nowMs - bucket.lastLeakMs);
            double leaked = (deltaMs / 1000.0) * leakRate;
            bucket.water = Math.max(0.0, bucket.water - leaked);
            bucket.lastLeakMs = nowMs;

            if (bucket.water + cost <= capacity) {
                bucket.water += cost;
                long remaining = (long) Math.floor(capacity - bucket.water);
                long resetSeconds = (long) Math.ceil(bucket.water / Math.max(0.1, leakRate));
                return RateLimitResult.allow(capacity, remaining, resetSeconds, getType().name(), key);
            } else {
                double excess = (bucket.water + cost) - capacity;
                long retryAfterSeconds = Math.max(1, (long) Math.ceil(excess / Math.max(0.1, leakRate)));
                long resetSeconds = (long) Math.ceil(bucket.water / Math.max(0.1, leakRate));
                return RateLimitResult.block(capacity, 0, resetSeconds, retryAfterSeconds, getType().name(), key);
            }
        }
    }
}
