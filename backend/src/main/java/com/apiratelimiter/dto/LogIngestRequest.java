package com.apiratelimiter.dto;

import java.util.List;

public record LogIngestRequest(
    List<LogEntry> entries
) {
    public record LogEntry(
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
        Long responseTimeMs
    ) {
        public LogEntry(
            String apiKeyPrefix,
            String endpoint,
            String method,
            String clientIp,
            Integer statusCode,
            Boolean isAllowed,
            Long rateLimitLimit,
            Long rateLimitRemaining,
            String algorithm,
            Long responseTimeMs
        ) {
            this(apiKeyPrefix, endpoint, method, clientIp, statusCode, isAllowed, rateLimitLimit, rateLimitRemaining, algorithm, null, responseTimeMs);
        }
    }
}
