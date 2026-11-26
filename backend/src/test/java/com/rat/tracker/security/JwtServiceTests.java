package com.rat.tracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import com.rat.tracker.enums.UserRole;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTests {

    @Test
    void activeKeySignsTokensWithKeyIdAndPreviousKeyRemainsValidDuringRotation() {
        String oldSecret = secret((byte) 1);
        String newSecret = secret((byte) 2);
        JwtService service = new JwtService(
                oldSecret,
                "previous=" + oldSecret + ",active=" + newSecret,
                "active",
                900_000L);
        UUID sessionId = UUID.randomUUID();
        String accessToken = service.generateToken("user@example.com", UserRole.VIEWER, sessionId);
        String previousToken = legacyToken(oldSecret, "user@example.com", sessionId);
        var user = User.withUsername("user@example.com").password("unused").roles("VIEWER").build();

        assertEquals("user@example.com", service.extractUsername(accessToken));
        assertEquals(sessionId, service.extractSessionId(accessToken));
        assertTrue(service.isTokenValid(accessToken, user));
        assertTrue(service.isTokenValid(previousToken, user));
        assertNotNull(accessToken);
    }

    @Test
    void unknownKeyIdsAreRejected() {
        String configuredSecret = secret((byte) 3);
        String unknownSecret = secret((byte) 4);
        JwtService service = new JwtService(configuredSecret, "active=" + configuredSecret, "active", 900_000L);
        String token = keyedToken("retired", unknownSecret, "user@example.com");
        var user = User.withUsername("user@example.com").password("unused").roles("VIEWER").build();

        assertFalse(service.isTokenValid(token, user));
    }

    private static String legacyToken(String encodedSecret, String email, UUID sessionId) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(encodedSecret));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(key)
                .compact();
    }

    private static String keyedToken(String keyId, String encodedSecret, String email) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(encodedSecret));
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(key)
                .compact();
    }

    private static String secret(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
