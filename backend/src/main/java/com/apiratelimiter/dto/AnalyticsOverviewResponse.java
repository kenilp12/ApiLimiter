package com.apiratelimiter.dto;

import java.util.Map;

public record AnalyticsOverviewResponse(
    long totalRequests,
    long allowedRequests,
    long blockedRequests,
    double rejectionRatePercentage,
    double averageResponseTimeMs,
    long activeKeysCount,
    long activePoliciesCount,
    Map<String, Long> algorithmBreakdown
) {}
