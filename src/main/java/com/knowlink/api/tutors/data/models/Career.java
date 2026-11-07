package com.knowlink.api.tutors.data.models;

import com.knowlink.api.tutors.data.enums.CareerType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "career", uniqueConstraints = @UniqueConstraint(
        name = "uk_career_institution_name",
        columnNames = {"institution_id", "name"}))
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

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @Builder.Default
    private CareerType type = CareerType.REGULAR;

    @OneToMany(mappedBy = "career")
    @Builder.Default
    private List<Subject> subjects = new ArrayList<>();

    @OneToMany(mappedBy = "career")
    @Builder.Default
    private List<TutorProfile> tutorProfiles = new ArrayList<>();
}