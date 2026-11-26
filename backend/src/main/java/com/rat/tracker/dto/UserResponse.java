package com.rat.tracker.dto;

import com.rat.tracker.enums.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        UserRole role,
        boolean active,
        boolean mustChangePassword,
        LocalDateTime createdAt) {
}
