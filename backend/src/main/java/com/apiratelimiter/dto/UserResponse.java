package com.apiratelimiter.dto;

import com.apiratelimiter.entity.User;
import com.apiratelimiter.enums.Role;
import java.time.Instant;

public record UserResponse(
    Long id,
    String username,
    String email,
    Role role,
    Instant createdAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}
