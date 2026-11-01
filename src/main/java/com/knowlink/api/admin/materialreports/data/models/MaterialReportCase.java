package com.knowlink.api.admin.materialreports.data.models;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "material_report_case", indexes = {
        @Index(name = "idx_mrc_status", columnList = "status"),
        @Index(name = "idx_mrc_status_opened", columnList = "status, opened_at"),
        @Index(name = "idx_mrc_resolved_at", columnList = "resolved_at"),
        @Index(name = "idx_mrc_material", columnList = "academic_material_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialReportCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "material_report_case_id", updatable = false, nullable = false)
    private UUID materialReportCaseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_material_id", nullable = false)
    private AcademicMaterial academicMaterial;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MaterialReportCaseStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private User assignedAdmin;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "reports_count", nullable = false)
    @Builder.Default
    private int reportsCount = 0;

    @Version
    @Column(name = "version")
    private Long version;
}