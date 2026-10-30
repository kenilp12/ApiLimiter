package com.apiratelimiter.dto;

import com.apiratelimiter.enums.AlgorithmType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PolicyCreateRequest(
    @NotBlank(message = "Policy name is required")
    String name,

    String description,

    @NotNull(message = "Algorithm type is required")
    AlgorithmType algorithm,

    @NotNull(message = "Limit value is required")
    @Min(value = 1, message = "Limit must be at least 1")
    Long limitValue,

    @NotNull(message = "Time window seconds is required")
    @Min(value = 1, message = "Time window must be at least 1 second")
    Long timeWindowSeconds,

    @Min(value = 1, message = "Burst capacity must be at least 1")
    Long burstCapacity,

    @Min(value = 0, message = "Refill rate must be positive")
    Double refillRate,

    Boolean isActive
) {}
