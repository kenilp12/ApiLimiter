package com.apiratelimiter.dto;

import com.apiratelimiter.entity.ApiKey;
import com.apiratelimiter.enums.ApiKeyStatus;
import java.time.Instant;

public record ApiKeyResponse(
    Long id,
    String keyPrefix,
    String name,
    ApiKeyStatus status,
    Long policyId,
    String policyName,
    String policyAlgorithm,
    String createdBy,
    Instant expiresAt,
    Instant createdAt
) {
    public static ApiKeyResponse fromEntity(ApiKey apiKey) {
        return new ApiKeyResponse(
            apiKey.getId(),
            apiKey.getKeyPrefix(),
            apiKey.getName(),
            apiKey.getStatus(),
            apiKey.getRateLimitPolicy().getId(),
            apiKey.getRateLimitPolicy().getName(),
            apiKey.getRateLimitPolicy().getAlgorithm().name(),
            apiKey.getUser() != null ? apiKey.getUser().getUsername() : "system",
            apiKey.getExpiresAt(),
            apiKey.getCreatedAt()
        );
    }
}
