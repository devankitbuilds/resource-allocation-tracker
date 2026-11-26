package com.rat.tracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rat.tracker.entity.SourceDocument;

public interface SourceDocumentRepository
        extends JpaRepository<SourceDocument, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SourceDocument document set document.uploadedBy = null "
            + "where document.uploadedBy.id = :userId")
    int clearUploaderReferences(@Param("userId") Long userId);
}