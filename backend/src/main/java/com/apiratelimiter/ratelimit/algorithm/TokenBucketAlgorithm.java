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
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketAlgorithm.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> tokenBucketScript;

    // Fallback local memory for testing when Redis is disabled
    private static class LocalBucket {
        double tokens;
        long lastRefillMs;
        LocalBucket(double tokens, long lastRefillMs) {
            this.tokens = tokens;
            this.lastRefillMs = lastRefillMs;
        }
    }
    private final ConcurrentHashMap<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();

    public TokenBucketAlgorithm(
        StringRedisTemplate redisTemplate,
        @Qualifier("tokenBucketScript") RedisScript<List<Long>> tokenBucketScript
    ) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.TOKEN_BUCKET;
    }

    @Override
    public RateLimitResult evaluate(String key, RateLimitRule rule, long cost) {
        long nowMs = Instant.now().toEpochMilli();
        long capacity = rule.burstCapacity() != null && rule.burstCapacity() > 0 ? rule.burstCapacity() : rule.limit();
        double refillRate = rule.refillRate() != null && rule.refillRate() > 0 
            ? rule.refillRate() 
            : (double) rule.limit() / Math.max(1, rule.windowSeconds());
        
        String redisKey = "rl:tb:" + key;

        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                List<Long> result = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(redisKey),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
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
            log.warn("Redis error during Token Bucket evaluation for key {}: {}. Using in-memory fallback.", key, e.getMessage());
        }

        // In-memory fallback
        LocalBucket bucket = localBuckets.computeIfAbsent(redisKey, k -> new LocalBucket(capacity, nowMs));
        synchronized (bucket) {
            long deltaMs = Math.max(0, nowMs - bucket.lastRefillMs);
            double tokensToAdd = (deltaMs / 1000.0) * refillRate;
            bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
            bucket.lastRefillMs = nowMs;

            if (bucket.tokens >= cost) {
                bucket.tokens -= cost;
                long remaining = (long) Math.floor(bucket.tokens);
                long resetSeconds = (long) Math.ceil((capacity - bucket.tokens) / Math.max(0.1, refillRate));
                return RateLimitResult.allow(capacity, remaining, resetSeconds, getType().name(), key);
            } else {
                double missing = cost - bucket.tokens;
                long retryAfterSeconds = Math.max(1, (long) Math.ceil(missing / Math.max(0.1, refillRate)));
                long resetSeconds = (long) Math.ceil((capacity - bucket.tokens) / Math.max(0.1, refillRate));
                return RateLimitResult.block(capacity, (long) Math.floor(bucket.tokens), resetSeconds, retryAfterSeconds, getType().name(), key);
            }
        }
    }
}
