package com.knowlink.api.tutors.data.models;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tutor_subject")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tutor_subject_id", updatable = false, nullable = false)
    private UUID tutorSubjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_profile_id", nullable = false)
    private TutorProfile tutorProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;
    
    @OneToMany(mappedBy = "tutorSubject", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AcademicMaterial> academicMaterials = new ArrayList<>();

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_per_hour", precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    @Enumerated(EnumType.STRING)
    @Column(name = "compensation_type", nullable = false)
    private CompensationType compensationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "tutor_subject_status", nullable = false)
    private TutorSubjectStatus tutorSubjectStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false)
    private Modality modality;
}
