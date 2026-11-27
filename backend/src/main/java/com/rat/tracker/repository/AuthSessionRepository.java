package com.rat.tracker.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rat.tracker.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    boolean existsByIdAndUser_EmailIgnoreCaseAndRevokedAtIsNullAndExpiresAtAfter(
            UUID id, String email, Instant now);

    Optional<AuthSession> findByIdAndUser_EmailIgnoreCase(UUID id, String email);

    List<AuthSession> findAllByUser_IdAndRevokedAtIsNull(Long userId);
}
