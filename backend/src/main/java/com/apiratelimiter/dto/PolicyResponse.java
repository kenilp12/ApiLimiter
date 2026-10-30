package com.apiratelimiter.dto;

import com.apiratelimiter.entity.RateLimitPolicy;
import com.apiratelimiter.enums.AlgorithmType;
import java.time.Instant;

public record PolicyResponse(
    Long id,
    String name,
    String description,
    AlgorithmType algorithm,
    String algorithmDisplayName,
    Long limitValue,
    Long timeWindowSeconds,
    Long burstCapacity,
    Double refillRate,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
    public static PolicyResponse fromEntity(RateLimitPolicy policy) {
        return new PolicyResponse(
            policy.getId(),
            policy.getName(),
            policy.getDescription(),
            policy.getAlgorithm(),
            policy.getAlgorithm().getDisplayName(),
            policy.getLimitValue(),
            policy.getTimeWindowSeconds(),
            policy.getBurstCapacity(),
            policy.getRefillRate(),
            policy.getIsActive(),
            policy.getCreatedAt(),
            policy.getUpdatedAt()
        );
    }
}
