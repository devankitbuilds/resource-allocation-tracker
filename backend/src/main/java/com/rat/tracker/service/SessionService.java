package com.rat.tracker.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rat.tracker.dto.LoginResponse;
import com.rat.tracker.entity.AppUser;
import com.rat.tracker.entity.AuthSession;
import com.rat.tracker.entity.RefreshToken;
import com.rat.tracker.repository.AuthSessionRepository;
import com.rat.tracker.repository.RefreshTokenRepository;
import com.rat.tracker.security.JwtService;


@Service
public class SessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final long refreshExpirationMs;

    public SessionService(
            AuthSessionRepository authSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshExpirationMs = refreshExpirationMs;
        if (refreshExpirationMs <= 0) {
            throw new IllegalArgumentException("JWT_REFRESH_EXPIRATION_MS must be greater than zero");
        }
    }

    @Transactional
    public LoginResponse startSession(AppUser user) {
        Instant now = Instant.now();
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusMillis(refreshExpirationMs));
        authSessionRepository.save(session);

        String rawRefreshToken = newRefreshToken();
        refreshTokenRepository.save(newRefreshTokenRecord(session, rawRefreshToken, now));
        return createResponse(user, session, rawRefreshToken);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw unauthorizedRefresh();
        }

        Instant now = Instant.now();
        RefreshToken currentToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(SessionService::unauthorizedRefresh);
        AuthSession session = currentToken.getSession();

        if (currentToken.getConsumedAt() != null) {
            revokeSession(session, now);
            throw unauthorizedRefresh();
        }
        if (currentToken.getRevokedAt() != null
                || !currentToken.getExpiresAt().isAfter(now)
                || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now)) {
            throw unauthorizedRefresh();
        }

        currentToken.setConsumedAt(now);
        refreshTokenRepository.saveAndFlush(currentToken);
        session.setLastRefreshedAt(now);
        authSessionRepository.save(session);

        String nextRefreshToken = newRefreshToken();
        refreshTokenRepository.save(newRefreshTokenRecord(session, nextRefreshToken, now));
        return createResponse(session.getUser(), session, nextRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken))
                .ifPresent(token -> revokeSession(token.getSession(), Instant.now()));
    }

    @Transactional
    public void revokeAllSessionsForUser(Long userId) {
        Instant now = Instant.now();
        List<AuthSession> sessions = authSessionRepository.findAllByUser_IdAndRevokedAtIsNull(userId);
        for (AuthSession session : sessions) {
            session.setRevokedAt(now);
        }
        authSessionRepository.saveAll(sessions);

        List<RefreshToken> tokens = refreshTokenRepository.findAllBySession_User_Id(userId);
        for (RefreshToken token : tokens) {
            if (token.getRevokedAt() == null && token.getConsumedAt() == null) {
                token.setRevokedAt(now);
            }
        }
        refreshTokenRepository.saveAll(tokens);
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId, String email) {
        return authSessionRepository.existsByIdAndUser_EmailIgnoreCaseAndRevokedAtIsNullAndExpiresAtAfter(
                sessionId, email, Instant.now());
    }

    private void revokeSession(AuthSession session, Instant now) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(now);
            authSessionRepository.save(session);
        }
        List<RefreshToken> tokens = refreshTokenRepository.findAllBySession_Id(session.getId());
        for (RefreshToken token : tokens) {
            if (token.getRevokedAt() == null && token.getConsumedAt() == null) {
                token.setRevokedAt(now);
            }
        }
        refreshTokenRepository.saveAll(tokens);
    }

    private RefreshToken newRefreshTokenRecord(AuthSession session, String rawToken, Instant now) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setSession(session);
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(session.getExpiresAt());
        return token;
    }

    private LoginResponse createResponse(AppUser user, AuthSession session, String rawRefreshToken) {
        return new LoginResponse(
                jwtService.generateToken(user.getEmail(), user.getRole(), session.getId()),
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getEmail(),
                user.getRole(),
                rawRefreshToken,
                Math.max(0, Duration.between(Instant.now(), session.getExpiresAt()).getSeconds()),
                user.isMustChangePassword());
    }

    private static String newRefreshToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for token hashing", exception);
        }
    }

    private static ResponseStatusException unauthorizedRefresh() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }
}
