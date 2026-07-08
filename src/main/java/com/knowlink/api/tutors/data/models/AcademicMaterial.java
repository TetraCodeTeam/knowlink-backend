package com.knowlink.api.tutors.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

import com.knowlink.api.tutors.data.enums.MaterialType;

@Entity
@Table(name = "academic_material")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "academic_material_id", updatable = false, nullable = false)
    private UUID academicMaterialId;

    @ManyToOne(fetch = FetchType.LAZY) 
    @JoinColumn(name = "tutor_subject_id", nullable = false)
    private TutorSubject tutorSubject;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "reports_count", nullable = false) 
    @Builder.Default
    private Integer reportsCount = 0;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false)
    private MaterialType materialType;
}