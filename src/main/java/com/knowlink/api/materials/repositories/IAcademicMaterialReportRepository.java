package com.knowlink.api.materials.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.knowlink.api.materials.data.models.AcademicMaterialReport;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface IAcademicMaterialReportRepository extends JpaRepository<AcademicMaterialReport, UUID> {

    boolean existsByMaterialAcademicMaterialIdAndReporterUserId(UUID materialId, UUID reporterUserId);

    @Query("""
            SELECT report FROM AcademicMaterialReport report
            JOIN FETCH report.material material
            JOIN FETCH material.tutorSubject tutorSubject
            JOIN FETCH tutorSubject.subject
            JOIN FETCH tutorSubject.tutorProfile tutorProfile
            JOIN FETCH tutorProfile.user
            WHERE tutorSubject.tutorProfile.user.userId = :tutorUserId
            ORDER BY report.reportedAt DESC
            """)
    List<AcademicMaterialReport> findByMaterial_TutorSubject_TutorProfile_User_UserIdOrderByReportedAtDesc(
            @Param("tutorUserId") UUID tutorUserId);

    @Query("""
            SELECT report.material.academicMaterialId
            FROM AcademicMaterialReport report
            WHERE report.reporter.userId = :reporterUserId
            AND report.material.academicMaterialId IN :materialIds
            """)
    Set<UUID> findReportedMaterialIds(
            @Param("reporterUserId") UUID reporterUserId,
            @Param("materialIds") Collection<UUID> materialIds);

    @Query("""
            SELECT report FROM AcademicMaterialReport report
            LEFT JOIN FETCH report.reviewedBy
            WHERE report.material.academicMaterialId = :materialId
            ORDER BY report.reportedAt DESC
            """)
    List<AcademicMaterialReport> findDetailsByMaterialId(@Param("materialId") UUID materialId);
}
