package com.apiratelimiter.dto;

import com.apiratelimiter.entity.ApiKey;
import com.apiratelimiter.entity.RateLimitPolicy;

public record ApiKeyValidationResponse(
    boolean valid,
    String keyPrefix,
    Long policyId,
    String policyName,
    String algorithm,
    Long limitValue,
    Long timeWindowSeconds,
    Long burstCapacity,
    Double refillRate,
    String reason
) {
    public static ApiKeyValidationResponse active(ApiKey key) {
        RateLimitPolicy policy = key.getRateLimitPolicy();
        return new ApiKeyValidationResponse(
            true,
            key.getKeyPrefix(),
            policy.getId(),
            policy.getName(),
            policy.getAlgorithm().name(),
            policy.getLimitValue(),
            policy.getTimeWindowSeconds(),
            policy.getBurstCapacity(),
            policy.getRefillRate(),
            null
        );
    }

    public static ApiKeyValidationResponse invalid(String reason) {
        return new ApiKeyValidationResponse(
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            reason
        );
    }
}
