package com.apiratelimiter.dto;

import com.apiratelimiter.entity.RequestLog;
import java.time.Instant;

public record RequestLogResponse(
    Long id,
    String apiKeyPrefix,
    String endpoint,
    String method,
    String clientIp,
    Integer statusCode,
    Boolean isAllowed,
    Long rateLimitLimit,
    Long rateLimitRemaining,
    String algorithm,
    String policyName,
    Long responseTimeMs,
    Instant timestamp
) {
    public RequestLogResponse(
        Long id,
        String apiKeyPrefix,
        String endpoint,
        String method,
        String clientIp,
        Integer statusCode,
        Boolean isAllowed,
        Long rateLimitLimit,
        Long rateLimitRemaining,
        String algorithm,
        Long responseTimeMs,
        Instant timestamp
    ) {
        this(id, apiKeyPrefix, endpoint, method, clientIp, statusCode, isAllowed, rateLimitLimit, rateLimitRemaining, algorithm, null, responseTimeMs, timestamp);
    }

    public static RequestLogResponse fromEntity(RequestLog log) {
        return new RequestLogResponse(
            log.getId(),
            log.getApiKeyPrefix(),
            log.getEndpoint(),
            log.getMethod(),
            log.getClientIp(),
            log.getStatusCode(),
            log.getIsAllowed(),
            log.getRateLimitLimit(),
            log.getRateLimitRemaining(),
            log.getAlgorithm(),
            log.getPolicyName(),
            log.getResponseTimeMs(),
            log.getCreatedAt()
        );
    }
}
