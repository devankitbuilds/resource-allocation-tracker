package com.rat.tracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rat.tracker.entity.AuditLog;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityAndEntityId(
            String entity,
            Long entityId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuditLog log set log.changedBy = null where log.changedBy.id = :userId")
    int clearActorReferences(@Param("userId") Long userId);
}