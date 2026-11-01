package com.knowlink.api.materials.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.users.data.models.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IAcademicMaterialReportRepository extends JpaRepository<AcademicMaterialReport, UUID> {

    boolean existsByMaterialAcademicMaterialIdAndReporterUserId(UUID materialId, UUID reporterUserId);

    boolean existsByMaterialAcademicMaterialId(UUID materialId);

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
            SELECT report FROM AcademicMaterialReport report
            JOIN FETCH report.material material
            JOIN FETCH material.tutorSubject tutorSubject
            JOIN FETCH tutorSubject.subject subject
            JOIN FETCH subject.career
            JOIN FETCH tutorSubject.tutorProfile tutorProfile
            JOIN FETCH tutorProfile.user
            JOIN FETCH report.reporter
            LEFT JOIN FETCH report.reviewedBy
            WHERE report.reporter.userId = :studentUserId
            ORDER BY report.reportedAt DESC
            """)
    List<AcademicMaterialReport> findStudentReports(@Param("studentUserId") UUID studentUserId);

    @Query("""
            SELECT report FROM AcademicMaterialReport report
            JOIN FETCH report.material material
            JOIN FETCH material.tutorSubject tutorSubject
            JOIN FETCH tutorSubject.subject subject
            JOIN FETCH subject.career
            JOIN FETCH tutorSubject.tutorProfile tutorProfile
            JOIN FETCH tutorProfile.user
            JOIN FETCH report.reporter
            LEFT JOIN FETCH report.reviewedBy
            WHERE report.academicMaterialReportId = :reportId
              AND report.reporter.userId = :studentUserId
            """)
    Optional<AcademicMaterialReport> findStudentReportDetails(
            @Param("reportId") UUID reportId,
            @Param("studentUserId") UUID studentUserId);

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

    @Query("""
            SELECT report FROM AcademicMaterialReport report
            JOIN FETCH report.reporter
            WHERE report.reportCase.materialReportCaseId = :caseId
            ORDER BY report.reportedAt DESC
            """)
    List<AcademicMaterialReport> findByReportCaseIdWithReporter(@Param("caseId") UUID caseId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AcademicMaterialReport report
               SET report.status = :status,
                   report.resolution = :resolution,
                   report.reviewedBy = :reviewedBy
             WHERE report.reportCase = :reportCase
            """)
    int resolveReportsByCase(@Param("reportCase") MaterialReportCase reportCase,
                             @Param("status") MaterialReportStatus status,
                             @Param("resolution") MaterialReportResolution resolution,
                             @Param("reviewedBy") User reviewedBy);
}
