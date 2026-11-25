package com.rat.tracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rat.tracker.entity.SourceDocument;

public interface SourceDocumentRepository
        extends JpaRepository<SourceDocument, Long> {
}