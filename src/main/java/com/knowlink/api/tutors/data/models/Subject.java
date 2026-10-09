package com.knowlink.api.tutors.data.models;

import com.knowlink.api.catalog.data.models.Institution;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "subjects", uniqueConstraints = @UniqueConstraint(columnNames = { "institution_id", "name" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subject_id", updatable = false, nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_basic", nullable = false)
    private boolean isBasic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToMany
    @JoinTable(name = "subject_career", joinColumns = @JoinColumn(name = "subject_id"), inverseJoinColumns = @JoinColumn(name = "career_id"))
    @Builder.Default
    private Set<Career> careers = new HashSet<>();
}