package com.apiratelimiter.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
    @NotBlank(message = "Username or email is required")
    String username,

    @NotBlank(message = "Password is required")
    String password
) {}
