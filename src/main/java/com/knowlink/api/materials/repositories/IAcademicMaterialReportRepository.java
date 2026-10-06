package com.knowlink.api.materials.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.knowlink.api.materials.data.models.AcademicMaterialReport;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface IAcademicMaterialReportRepository extends JpaRepository<AcademicMaterialReport, UUID> {

    boolean existsByMaterialAcademicMaterialIdAndReporterUserId(UUID materialId, UUID reporterUserId);

    @Query("""
            SELECT report.material.academicMaterialId
            FROM AcademicMaterialReport report
            WHERE report.reporter.userId = :reporterUserId
            AND report.material.academicMaterialId IN :materialIds
            """)
    Set<UUID> findReportedMaterialIds(
            @Param("reporterUserId") UUID reporterUserId,
            @Param("materialIds") Collection<UUID> materialIds);
}