package com.rat.tracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rat.tracker.entity.Project;

public interface ProjectRepository
        extends JpaRepository<Project, Long> {

    Optional<Project> findByCode(String code);
}