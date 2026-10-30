package com.apiratelimiter.dto;

public record AuthResponse(
    String token,
    String tokenType,
    Long expiresIn,
    UserResponse user
) {
    public AuthResponse(String token, Long expiresIn, UserResponse user) {
        this(token, "Bearer", expiresIn, user);
    }
}
