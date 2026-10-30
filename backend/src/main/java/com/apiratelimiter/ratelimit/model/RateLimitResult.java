package com.apiratelimiter.ratelimit.model;

public record RateLimitResult(
    boolean allowed,
    long limit,
    long remaining,
    long resetSeconds,
    long retryAfterSeconds,
    String algorithm,
    String key
) {
    public static RateLimitResult allow(long limit, long remaining, long resetSeconds, String algorithm, String key) {
        return new RateLimitResult(true, limit, remaining, resetSeconds, 0, algorithm, key);
    }

    public static RateLimitResult block(long limit, long remaining, long resetSeconds, long retryAfterSeconds, String algorithm, String key) {
        return new RateLimitResult(false, limit, remaining, resetSeconds, retryAfterSeconds, algorithm, key);
    }
}
