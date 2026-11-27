package com.rat.tracker.security;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import java.security.Key;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.rat.tracker.enums.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final Map<String, SecretKey> signingKeys;
    private final SecretKey legacyKey;
    private final String activeKeyId;
    private final JwtParser parser;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret:}") String legacySecret,
            @Value("${app.jwt.signing-keys:}") String signingKeysConfig,
            @Value("${app.jwt.active-key-id:primary}") String activeKeyId,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION_MS must be greater than zero");
        }
        this.legacyKey = legacySecret.isBlank() ? null : decodeKey(legacySecret);
        this.activeKeyId = activeKeyId;
        this.signingKeys = parseSigningKeys(signingKeysConfig, activeKeyId, legacyKey);
        if (!signingKeys.containsKey(activeKeyId)) {
            throw new IllegalArgumentException("JWT_ACTIVE_KEY_ID must identify a key in JWT_SIGNING_KEYS");
        }
        this.expirationMs = expirationMs;
        this.parser = Jwts.parser()
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(ProtectedHeader header) {
                        String keyId = header.getKeyId();
                        if (keyId == null) {
                            if (JwtService.this.legacyKey == null) {
                                throw new JwtException("JWT has no key id and no legacy key is configured");
                            }
                            return JwtService.this.legacyKey;
                        }
                        SecretKey key = JwtService.this.signingKeys.get(keyId);
                        if (key == null) {
                            throw new JwtException("JWT was signed with an unknown key id");
                        }
                        return key;
                    }
                })
                .build();
    }

    public String generateToken(String email, UserRole role, UUID sessionId) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(activeKeyId)
                .and()
                .subject(email)
                .claim("role", role.name())
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(expirationMs)))
                .signWith(signingKeys.get(activeKeyId))
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID extractSessionId(String token) {
        String sessionId = parseClaims(token).get("sid", String.class);
        if (sessionId == null) {
            throw new IllegalArgumentException("JWT is missing its session identifier");
        }
        return UUID.fromString(sessionId);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            Date expiration = claims.getExpiration();
            return userDetails.getUsername().equalsIgnoreCase(claims.getSubject())
                    && expiration != null
                    && expiration.after(new Date());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    private Claims parseClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    private static Map<String, SecretKey> parseSigningKeys(
            String configuration,
            String activeKeyId,
            SecretKey legacyKey) {
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        if (configuration.isBlank()) {
            if (legacyKey == null) {
                throw new IllegalArgumentException("Set JWT_SECRET or JWT_SIGNING_KEYS");
            }
            keys.put(activeKeyId, legacyKey);
            return Map.copyOf(keys);
        }

        for (String entry : configuration.split(",")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalArgumentException(
                        "JWT_SIGNING_KEYS entries must use key-id=base64-key format");
            }
            String keyId = entry.substring(0, separator).trim();
            if (!keyId.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IllegalArgumentException("JWT key ids may contain only letters, digits, dot, _ or -");
            }
            if (keys.putIfAbsent(keyId, decodeKey(entry.substring(separator + 1).trim())) != null) {
                throw new IllegalArgumentException("JWT_SIGNING_KEYS contains duplicate key id: " + keyId);
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("JWT_SIGNING_KEYS must contain at least one key");
        }
        return Map.copyOf(keys);
    }

    private static SecretKey decodeKey(String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "JWT signing keys must be Base64-encoded keys of at least 32 bytes",
                    exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT signing keys must be Base64-encoded keys of at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
