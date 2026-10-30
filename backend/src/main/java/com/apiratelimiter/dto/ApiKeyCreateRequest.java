package com.apiratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ApiKeyCreateRequest(
    @NotBlank(message = "API key name is required")
    String name,

    @NotNull(message = "Rate limit policy ID is required")
    Long policyId,

    Instant expiresAt
) {}
