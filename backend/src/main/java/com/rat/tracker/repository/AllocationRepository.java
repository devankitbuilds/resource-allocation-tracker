package com.rat.tracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rat.tracker.entity.Allocation;

public interface AllocationRepository
        extends JpaRepository<Allocation, Long> {

    List<Allocation> findByResourceId(Long resourceId);

    List<Allocation> findByProjectId(Long projectId);
}