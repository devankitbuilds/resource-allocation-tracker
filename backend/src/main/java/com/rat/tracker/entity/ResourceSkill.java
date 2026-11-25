package com.rat.tracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "resource_skill")
@Getter
@Setter
@NoArgsConstructor
public class ResourceSkill {

    @EmbeddedId
    private ResourceSkillId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("resourceId")
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column
    private Integer proficiency;

    @Column(name = "years", precision = 5, scale = 2)
    private BigDecimal years = BigDecimal.ZERO;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;
}