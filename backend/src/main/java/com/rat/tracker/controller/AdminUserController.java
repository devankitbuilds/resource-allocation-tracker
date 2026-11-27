package com.rat.tracker.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rat.tracker.dto.TemporaryPasswordResponse;
import com.rat.tracker.dto.CreateUserRequest;
import com.rat.tracker.dto.UpdateUserRequest;
import com.rat.tracker.dto.UpdateUserStatusRequest;
import com.rat.tracker.dto.UserResponse;
import com.rat.tracker.service.UserManagementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserManagementService userManagementService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userManagementService.createUser(request, authentication.getName()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> listUsers(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return userManagementService.listUsers(search, pageable);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUser(@PathVariable Long userId) {
        return userManagementService.getUser(userId);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        return userManagementService.updateUser(userId, request, authentication.getName());
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            Authentication authentication) {
        return userManagementService.updateStatus(userId, request, authentication.getName());
    }

    @PostMapping("/{userId}/temporary-password")
    @PreAuthorize("hasRole('ADMIN')")
    public TemporaryPasswordResponse resetTemporaryPassword(
            @PathVariable Long userId,
            Authentication authentication) {
        return userManagementService.resetTemporaryPassword(userId, authentication.getName());
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long userId,
            Authentication authentication) {
        userManagementService.deleteUser(userId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
