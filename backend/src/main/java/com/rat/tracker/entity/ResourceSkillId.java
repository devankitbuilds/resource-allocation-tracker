package com.rat.tracker.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ResourceSkillId implements Serializable {

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "skill_id")
    private Long skillId;
}