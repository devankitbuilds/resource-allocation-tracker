package com.rat.tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.rat.tracker.dto.CreateUserRequest;
import com.rat.tracker.dto.UpdateUserStatusRequest;
import com.rat.tracker.entity.AppUser;
import com.rat.tracker.entity.AuditLog;
import com.rat.tracker.entity.AuthSession;
import com.rat.tracker.entity.RefreshToken;
import com.rat.tracker.enums.UserRole;
import com.rat.tracker.repository.AppUserRepository;
import com.rat.tracker.repository.AuditLogRepository;
import com.rat.tracker.repository.AuthSessionRepository;
import com.rat.tracker.repository.RefreshTokenRepository;
import com.rat.tracker.repository.SourceDocumentRepository;
import com.rat.tracker.security.JwtService;

class AuthLifecycleServiceTests {

    @Test
    void adminProvisioningCreatesAnActiveAccountRequiringPasswordChangeAndAuditsIt() {
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        AppUser admin = user(1L, "admin@example.com", UserRole.ADMIN);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(users.findByEmailIgnoreCase("new.manager@example.com")).thenReturn(Optional.empty());
        when(encoder.encode("TemporaryPass123")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail(" New.Manager@Example.com ");
        request.setPassword("TemporaryPass123");
        request.setRole(UserRole.MANAGER);

        UserManagementService service = userManagementService(
                users, auditLogs, mock(SourceDocumentRepository.class), encoder, mock(SessionService.class));
        var response = service.createUser(request, admin.getEmail());

        assertEquals(2L, response.id());
        assertEquals("new.manager@example.com", response.email());
        assertEquals(UserRole.MANAGER, response.role());
        assertEquals(true, response.active());
        assertEquals(true, response.mustChangePassword());
        ArgumentCaptor<AuditLog> savedLog = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(savedLog.capture());
        assertEquals("CREATE", savedLog.getValue().getAction());
        assertEquals(1L, savedLog.getValue().getChangedBy().getId());
    }

    @Test
    void duplicateEmailIsRejectedDuringAdminProvisioning() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(user(1L, "admin@example.com", UserRole.ADMIN)));
        when(users.findByEmailIgnoreCase("existing@example.com"))
                .thenReturn(Optional.of(user(2L, "existing@example.com", UserRole.VIEWER)));
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("existing@example.com");
        request.setPassword("TemporaryPass123");
        request.setRole(UserRole.VIEWER);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userManagementService(
                        users, mock(AuditLogRepository.class), mock(SourceDocumentRepository.class),
                        mock(PasswordEncoder.class), mock(SessionService.class))
                        .createUser(request, "admin@example.com"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void deactivatingAUserRevokesSessionsAndWritesAnAuditRecord() {
        AppUserRepository users = mock(AppUserRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        SessionService sessions = mock(SessionService.class);
        AppUser admin = user(1L, "admin@example.com", UserRole.ADMIN);
        AppUser target = user(2L, "viewer@example.com", UserRole.VIEWER);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(users.saveAndFlush(target)).thenReturn(target);
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setActive(false);

        UserManagementService service = userManagementService(
                users, auditLogs, mock(SourceDocumentRepository.class),
                mock(PasswordEncoder.class), sessions);
        var response = service.updateStatus(2L, request, "admin@example.com");

        assertEquals(false, response.active());
        verify(sessions).revokeAllSessionsForUser(2L);
        ArgumentCaptor<AuditLog> savedLog = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(savedLog.capture());
        assertEquals("DEACTIVATE", savedLog.getValue().getAction());
    }

    @Test
    void deactivatingTheOnlyActiveAdminIsRejected() {
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser actor = user(1L, "manager@example.com", UserRole.MANAGER);
        AppUser admin = user(2L, "admin@example.com", UserRole.ADMIN);
        when(users.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(actor));
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(users.findActiveUsersByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of(admin));
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setActive(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userManagementService(
                        users, mock(AuditLogRepository.class), mock(SourceDocumentRepository.class),
                        mock(PasswordEncoder.class), mock(SessionService.class))
                        .updateStatus(2L, request, "manager@example.com"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(true, admin.isActive());
    }

    @Test
    void temporaryPasswordResetReturnsGeneratedSecretOnlyToTheAdminAndRevokesSessions() {
        AppUserRepository users = mock(AppUserRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        SessionService sessions = mock(SessionService.class);
        AppUser admin = user(1L, "admin@example.com", UserRole.ADMIN);
        AppUser target = user(2L, "manager@example.com", UserRole.MANAGER);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(users.saveAndFlush(target)).thenReturn(target);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("encoded-temp-password");

        var response = userManagementService(
                users, auditLogs, mock(SourceDocumentRepository.class), encoder, sessions)
                .resetTemporaryPassword(2L, "admin@example.com");

        assertEquals("manager@example.com", response.email());
        assertNotNull(response.temporaryPassword());
        assertEquals(32, response.temporaryPassword().length());
        assertNotEquals("encoded-temp-password", response.temporaryPassword());
        assertEquals(true, target.isMustChangePassword());
        verify(sessions).revokeAllSessionsForUser(2L);
    }

    @Test
    void forcedPasswordChangeClearsFlagAuditsChangeAndRequiresFreshLogin() {
        AppUserRepository users = mock(AppUserRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        SessionService sessions = mock(SessionService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AppUser user = user(2L, "manager@example.com", UserRole.MANAGER);
        user.setMustChangePassword(true);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.encode("NewSecurePass123")).thenReturn("new-bcrypt-hash");
        when(users.saveAndFlush(user)).thenReturn(user);

        userManagementService(users, auditLogs, mock(SourceDocumentRepository.class), encoder, sessions)
                .changeOwnPassword(user.getEmail(), "NewSecurePass123");

        assertEquals("new-bcrypt-hash", user.getPasswordHash());
        assertEquals(false, user.isMustChangePassword());
        verify(sessions).revokeAllSessionsForUser(user.getId());
        ArgumentCaptor<AuditLog> savedLog = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(savedLog.capture());
        assertEquals("PASSWORD_CHANGED", savedLog.getValue().getAction());
    }

    @Test
    void refreshRotatesTheOpaqueTokenAndKeepsOnlyItsHash() {
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        JwtService jwtService = mock(JwtService.class);
        SessionService service = new SessionService(sessions, refreshTokens, jwtService, 604_800_000L);
        AppUser user = user(11L, "viewer@example.com", UserRole.VIEWER);
        AuthSession session = activeSession(user);
        RefreshToken oldToken = refreshToken(session, "old-refresh-token");
        when(refreshTokens.findByTokenHashForUpdate(hash("old-refresh-token"))).thenReturn(Optional.of(oldToken));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(anyString(), any(UserRole.class), any(UUID.class)))
                .thenReturn("new-access-token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);

        var response = service.refresh("old-refresh-token");

        assertNotNull(oldToken.getConsumedAt());
        assertNotEquals("old-refresh-token", response.refreshToken());
        verify(refreshTokens).saveAndFlush(oldToken);
        ArgumentCaptor<RefreshToken> nextToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(nextToken.capture());
        assertEquals(hash(response.refreshToken()), nextToken.getValue().getTokenHash());
        assertNotEquals(response.refreshToken(), nextToken.getValue().getTokenHash());
        assertEquals(900L, response.expiresIn());
        assertEquals(false, response.mustChangePassword());
    }

    @Test
    void replayingAConsumedRefreshTokenRevokesItsWholeSession() {
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        SessionService service = new SessionService(sessions, refreshTokens, mock(JwtService.class), 604_800_000L);
        AuthSession session = activeSession(user(12L, "viewer@example.com", UserRole.VIEWER));
        RefreshToken consumedToken = refreshToken(session, "replayed-token");
        consumedToken.setConsumedAt(Instant.now().minusSeconds(1));
        when(refreshTokens.findByTokenHashForUpdate(hash("replayed-token"))).thenReturn(Optional.of(consumedToken));
        when(refreshTokens.findAllBySession_Id(session.getId())).thenReturn(List.of(consumedToken));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.refresh("replayed-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertNotNull(session.getRevokedAt());
        verify(sessions).save(session);
    }

    private static UserManagementService userManagementService(
            AppUserRepository users,
            AuditLogRepository auditLogs,
            SourceDocumentRepository documents,
            PasswordEncoder encoder,
            SessionService sessions) {
        return new UserManagementService(users, auditLogs, documents, encoder, sessions);
    }

    private static AppUser user(Long id, String email, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private static AuthSession activeSession(AppUser user) {
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(600_000));
        return session;
    }

    private static RefreshToken refreshToken(AuthSession session, String rawToken) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setSession(session);
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(session.getExpiresAt());
        return token;
    }

    private static String hash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
