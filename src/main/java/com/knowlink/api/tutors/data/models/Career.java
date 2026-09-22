package com.knowlink.api.tutors.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "career")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Career {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "career_id", updatable = false, nullable = false)
    private UUID careerId;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "career")
    @Builder.Default
    private List<Subject> subjects = new ArrayList<>();

    @OneToMany(mappedBy = "career")
    @Builder.Default
    private List<TutorProfile> tutorProfiles = new ArrayList<>();
}