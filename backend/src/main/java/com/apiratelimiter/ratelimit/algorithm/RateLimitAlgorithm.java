package com.apiratelimiter.ratelimit.algorithm;

import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;

public interface RateLimitAlgorithm {
    AlgorithmType getType();
    RateLimitResult evaluate(String key, RateLimitRule rule, long cost);
}
