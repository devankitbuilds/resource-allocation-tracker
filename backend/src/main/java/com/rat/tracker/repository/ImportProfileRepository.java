package com.rat.tracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rat.tracker.entity.ImportProfile;

public interface ImportProfileRepository
        extends JpaRepository<ImportProfile, Long> {
}
