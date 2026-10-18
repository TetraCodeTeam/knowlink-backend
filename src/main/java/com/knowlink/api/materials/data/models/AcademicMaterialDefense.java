package com.knowlink.api.materials.data.models;

import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "academic_material_defense", indexes = @Index(
        name = "idx_material_defense_material_submitted", columnList = "academic_material_id, submitted_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicMaterialDefense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "academic_material_defense_id", updatable = false, nullable = false)
    private UUID defenseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_material_id", nullable = false)
    private AcademicMaterial material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_user_id", nullable = false)
    private User tutor;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now(AppTimeZone.ZONE);
        }
    }
}
