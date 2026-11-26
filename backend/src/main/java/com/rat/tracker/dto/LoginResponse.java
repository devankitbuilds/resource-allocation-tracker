package com.rat.tracker.dto;

import com.rat.tracker.enums.UserRole;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String email,
        UserRole role,
        String refreshToken,
        long refreshExpiresIn,
        boolean mustChangePassword) {
}
