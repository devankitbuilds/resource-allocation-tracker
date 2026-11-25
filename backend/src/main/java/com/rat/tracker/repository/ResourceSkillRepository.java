package com.rat.tracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rat.tracker.entity.ResourceSkill;
import com.rat.tracker.entity.ResourceSkillId;

public interface ResourceSkillRepository
        extends JpaRepository<ResourceSkill, ResourceSkillId> {

    List<ResourceSkill> findByResourceId(Long resourceId);

    List<ResourceSkill> findBySkillId(Long skillId);
}