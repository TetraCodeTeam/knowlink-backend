package com.knowlink.api.materials.data.models;

import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "academic_material_report", uniqueConstraints = @UniqueConstraint(
        name = "uk_material_report_material_reporter",
        columnNames = { "academic_material_id", "reporter_user_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicMaterialReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "academic_material_report_id", updatable = false, nullable = false)
    private UUID academicMaterialReportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_material_id", nullable = false)
    private AcademicMaterial material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private MaterialReportReason reason;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    @PrePersist
    protected void onCreate() {
        if (reportedAt == null) {
            reportedAt = LocalDateTime.now();
        }
    }
}