package com.rat.tracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rat.tracker.entity.AuditLog;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityAndEntityId(
            String entity,
            Long entityId
    );
}