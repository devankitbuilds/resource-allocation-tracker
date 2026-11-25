package com.rat.tracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rat.tracker.entity.Resource;

public interface ResourceRepository
        extends JpaRepository<Resource, Long> {

    Optional<Resource> findByEmail(String email);

    Optional<Resource> findByEmployeeId(String employeeId);

    List<Resource> findByNameContainingIgnoreCase(String name);
}