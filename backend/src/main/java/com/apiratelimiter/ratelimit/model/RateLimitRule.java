package com.apiratelimiter.ratelimit.model;

import com.apiratelimiter.enums.AlgorithmType;

public record RateLimitRule(
    String key,
    AlgorithmType algorithm,
    long limit,
    long windowSeconds,
    Long burstCapacity,
    Double refillRate
) {
    public static RateLimitRule of(String key, AlgorithmType algorithm, long limit, long windowSeconds, Long burstCapacity, Double refillRate) {
        return new RateLimitRule(key, algorithm, limit, windowSeconds, burstCapacity, refillRate);
    }
}
