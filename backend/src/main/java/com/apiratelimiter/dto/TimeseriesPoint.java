package com.apiratelimiter.dto;

public record TimeseriesPoint(
    String timestamp,
    long totalRequests,
    long allowedRequests,
    long blockedRequests
) {}
