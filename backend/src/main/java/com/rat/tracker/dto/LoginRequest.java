package com.rat.tracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// DTO that represents the data received during login
@Getter
@Setter
public class LoginRequest {

    // User must provide a valid email
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    // Password must not be empty
    @NotBlank(message = "Password is required")
    private String password;
}