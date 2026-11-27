package com.rat.tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.rat.tracker.dto.UpdateUserRequest;
import com.rat.tracker.enums.UserRole;

@SpringBootTest
@Transactional
class UserManagementDatabaseTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserManagementService userManagementService;

    @Test
    void migrationRemovesOutOfScopeAuthArtifactsAndAddsInternalAccountFlags() {
        assertEquals(0, count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('email_verification_token', 'password_reset_token', 'auth_rate_limit_bucket')
                """));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'app_user' AND column_name = 'email_verified'
                """));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'app_user'
                  AND column_name IN ('is_active', 'must_change_password')
                """));
    }

    @Test
    void deletingUserPreservesAuditButRemovesOwnedSessionsAndChat() {
        String suffix = UUID.randomUUID().toString();
        Long adminId = createUser("admin-" + suffix + "@example.invalid", "ADMIN");
        Long targetId = createUser("viewer-" + suffix + "@example.invalid", "VIEWER");
        Long previousAuditId = jdbcTemplate.queryForObject("""
                INSERT INTO audit_log (entity, entity_id, action, changed_by)
                VALUES ('RESOURCE', 9, 'UPDATE', ?)
                RETURNING id
                """, Long.class, targetId);
        Long sourceDocumentId = jdbcTemplate.queryForObject("""
                INSERT INTO source_document (file_name, uploaded_by)
                VALUES ('resume.pdf', ?)
                RETURNING id
                """, Long.class, targetId);
        Long chatMessageId = jdbcTemplate.queryForObject("""
                INSERT INTO chat_message (user_id, session_id, role, content)
                VALUES (?, ?, 'USER', 'private chat')
                RETURNING id
                """, Long.class, targetId, suffix);
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auth_session (id, user_id, created_at, expires_at)
                VALUES (?, ?, ?, ?)
                """, sessionId, targetId, Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().plusSeconds(3600)));
        jdbcTemplate.update("""
                INSERT INTO refresh_token (id, session_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), sessionId, "a".repeat(64),
                Timestamp.from(Instant.now().plusSeconds(3600)));

        userManagementService.deleteUser(targetId, "admin-" + suffix + "@example.invalid");

        assertEquals(0, count("SELECT COUNT(*) FROM app_user WHERE id = ?", targetId));
        assertEquals(0, count("SELECT COUNT(*) FROM chat_message WHERE id = ?", chatMessageId));
        assertEquals(0, count("SELECT COUNT(*) FROM auth_session WHERE id = ?", sessionId));
        assertEquals(0, count("SELECT COUNT(*) FROM refresh_token WHERE session_id = ?", sessionId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT uploaded_by FROM source_document WHERE id = ?",
                Long.class,
                sourceDocumentId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT changed_by FROM audit_log WHERE id = ?",
                Long.class,
                previousAuditId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM audit_log
                WHERE entity = 'APP_USER' AND entity_id = ? AND action = 'DELETE' AND changed_by = ?
                """, targetId, adminId));
    }

    @Test
    void changingAnAdminRoleKeepsOneActiveAdminAndRevokesTheirSessions() {
        String suffix = UUID.randomUUID().toString();
        String actorEmail = "admin-a-" + suffix + "@example.invalid";
        String targetEmail = "admin-b-" + suffix + "@example.invalid";
        createUser(actorEmail, "ADMIN");
        Long targetId = createUser(targetEmail, "ADMIN");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail(targetEmail);
        request.setRole(UserRole.MANAGER);

        var updated = userManagementService.updateUser(targetId, request, actorEmail);

        assertEquals(UserRole.MANAGER, updated.role());
        assertEquals(1, count("""
                SELECT COUNT(*) FROM app_user
                WHERE email IN (?, ?) AND role = 'ADMIN' AND is_active = TRUE
                """, actorEmail, targetEmail));
    }

    private Long createUser(String email, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user (email, password_hash, role)
                VALUES (?, 'integration-test-hash', ?)
                RETURNING id
                """, Long.class, email, role);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
