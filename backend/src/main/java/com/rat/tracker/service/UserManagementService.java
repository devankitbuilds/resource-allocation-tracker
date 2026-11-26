package com.rat.tracker.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rat.tracker.dto.CreateUserRequest;
import com.rat.tracker.dto.TemporaryPasswordResponse;
import com.rat.tracker.dto.UpdateUserRequest;
import com.rat.tracker.dto.UpdateUserStatusRequest;
import com.rat.tracker.dto.UserResponse;
import com.rat.tracker.entity.AppUser;
import com.rat.tracker.entity.AuditLog;
import com.rat.tracker.enums.UserRole;
import com.rat.tracker.repository.AppUserRepository;
import com.rat.tracker.repository.AuditLogRepository;
import com.rat.tracker.repository.SourceDocumentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final AuditLogRepository auditLogRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String search, Pageable pageable) {
        Page<AppUser> users = search == null || search.isBlank()
                ? appUserRepository.findAll(pageable)
                : appUserRepository.findByEmailContainingIgnoreCase(search.trim(), pageable);
        return users.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, String actorEmail) {
        AppUser actor = findActor(actorEmail);
        String email = normalizeEmail(request.getEmail());
        if (appUserRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw conflict("An account with this email already exists");
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setActive(true);
        user.setMustChangePassword(true);
        AppUser savedUser = appUserRepository.saveAndFlush(user);
        recordAudit("CREATE", savedUser, actor, null, snapshot(savedUser));
        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request, String actorEmail) {
        AppUser actor = findActor(actorEmail);
        AppUser user = findUser(userId);
        JsonNode oldValue = snapshot(user);
        String email = normalizeEmail(request.getEmail());
        appUserRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw conflict("An account with this email already exists");
                });

        if (user.isActive() && user.getRole() == UserRole.ADMIN
                && request.getRole() != UserRole.ADMIN) {
            requireAnotherActiveAdmin(user);
        }

        boolean emailChanged = !user.getEmail().equalsIgnoreCase(email);
        boolean roleChanged = user.getRole() != request.getRole();
        user.setEmail(email);
        user.setRole(request.getRole());
        AppUser savedUser = appUserRepository.saveAndFlush(user);
        if (emailChanged || roleChanged) {
            sessionService.revokeAllSessionsForUser(userId);
        }
        recordAudit("UPDATE", savedUser, actor, oldValue, snapshot(savedUser));
        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse updateStatus(
            Long userId,
            UpdateUserStatusRequest request,
            String actorEmail) {
        AppUser actor = findActor(actorEmail);
        AppUser user = findUser(userId);
        boolean newActiveStatus = request.getActive();
        if (user.isActive() == newActiveStatus) {
            return toResponse(user);
        }
        if (!newActiveStatus && user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw conflict("You cannot deactivate your own account");
        }
        JsonNode oldValue = snapshot(user);
        if (!newActiveStatus && user.getRole() == UserRole.ADMIN) {
            requireAnotherActiveAdmin(user);
        }

        user.setActive(newActiveStatus);
        AppUser savedUser = appUserRepository.saveAndFlush(user);
        if (!newActiveStatus) {
            sessionService.revokeAllSessionsForUser(userId);
        }
        recordAudit(
                newActiveStatus ? "ACTIVATE" : "DEACTIVATE",
                savedUser,
                actor,
                oldValue,
                snapshot(savedUser));
        return toResponse(savedUser);
    }

    @Transactional
    public TemporaryPasswordResponse resetTemporaryPassword(Long userId, String actorEmail) {
        AppUser actor = findActor(actorEmail);
        AppUser user = findUser(userId);
        String temporaryPassword = generateTemporaryPassword();
        JsonNode oldValue = snapshot(user);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        AppUser savedUser = appUserRepository.saveAndFlush(user);
        sessionService.revokeAllSessionsForUser(userId);
        recordAudit("TEMPORARY_PASSWORD_RESET", savedUser, actor, oldValue, snapshot(savedUser));
        return new TemporaryPasswordResponse(savedUser.getEmail(), temporaryPassword);
    }

    @Transactional
    public void changeOwnPassword(String email, String newPassword) {
        AppUser user = findActor(email);
        if (!user.isMustChangePassword()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "A password change is not required for this account");
        }

        JsonNode oldValue = snapshot(user);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        AppUser savedUser = appUserRepository.saveAndFlush(user);
        sessionService.revokeAllSessionsForUser(user.getId());
        recordAudit("PASSWORD_CHANGED", savedUser, savedUser, oldValue, snapshot(savedUser));
    }

    @Transactional
    public void deleteUser(Long userId, String actorEmail) {
        AppUser actor = findActor(actorEmail);
        AppUser user = findUser(userId);
        if (user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw conflict("You cannot permanently delete your own account");
        }
        if (user.isActive() && user.getRole() == UserRole.ADMIN) {
            requireAnotherActiveAdmin(user);
        }

        recordAudit("DELETE", user, actor, snapshot(user), null);
        auditLogRepository.flush();
        auditLogRepository.clearActorReferences(userId);
        sourceDocumentRepository.clearUploaderReferences(userId);
        appUserRepository.deleteById(userId);
        appUserRepository.flush();
    }

    private void requireAnotherActiveAdmin(AppUser target) {
        List<AppUser> activeAdmins =
                appUserRepository.findActiveUsersByRoleForUpdate(UserRole.ADMIN);
        if (activeAdmins.size() <= 1
                || activeAdmins.stream().noneMatch(admin -> !admin.getId().equals(target.getId()))) {
            throw conflict("At least one active Admin account must remain");
        }
    }

    private AppUser findActor(String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.isMustChangePassword(),
                user.getCreatedAt());
    }

    private JsonNode snapshot(AppUser user) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (user.getId() != null) {
            node.put("id", user.getId());
        }
        node.put("email", user.getEmail());
        node.put("role", user.getRole().name());
        node.put("active", user.isActive());
        node.put("mustChangePassword", user.isMustChangePassword());
        return node;
    }

    private void recordAudit(
            String action,
            AppUser subject,
            AppUser actor,
            JsonNode oldValue,
            JsonNode newValue) {
        AuditLog log = new AuditLog();
        log.setEntity("APP_USER");
        log.setEntityId(subject.getId());
        log.setAction(action);
        log.setChangedBy(actor);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        auditLogRepository.save(log);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateTemporaryPassword() {
        byte[] passwordBytes = new byte[24];
        SECURE_RANDOM.nextBytes(passwordBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(passwordBytes);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
