package com.knowlink.api.admin.materialreports.repositories;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.users.data.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IMaterialReportCaseRepository extends JpaRepository<MaterialReportCase, UUID> {

    @Query("""
            SELECT c FROM MaterialReportCase c
            WHERE c.academicMaterial.academicMaterialId = :materialId
              AND c.status IN (com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.PENDING,
                               com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.IN_REVIEW)
            ORDER BY c.openedAt DESC
            """)
    List<MaterialReportCase> findOpenCasesByMaterialId(@Param("materialId") UUID materialId);

    @Query(value = """
            SELECT c FROM MaterialReportCase c
            JOIN FETCH c.academicMaterial m
            JOIN FETCH m.tutorSubject ts
            JOIN FETCH ts.subject s
            JOIN FETCH ts.tutorProfile tp
            JOIN FETCH tp.user tu
            LEFT JOIN FETCH c.assignedAdmin
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND c.status = :status
            """,
            countQuery = """
            SELECT COUNT(c) FROM MaterialReportCase c
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND c.status = :status
            """)
    Page<MaterialReportCase> findCasesByStatus(@Param("status") MaterialReportCaseStatus status,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               Pageable pageable);

    @Query(value = """
            SELECT c FROM MaterialReportCase c
            JOIN FETCH c.academicMaterial m
            JOIN FETCH m.tutorSubject ts
            JOIN FETCH ts.subject s
            JOIN FETCH ts.tutorProfile tp
            JOIN FETCH tp.user tu
            JOIN FETCH c.assignedAdmin aa
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND c.status = :status
              AND aa.userId = :assignedAdminId
            """,
            countQuery = """
            SELECT COUNT(c) FROM MaterialReportCase c
            JOIN c.assignedAdmin aa
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND c.status = :status
              AND aa.userId = :assignedAdminId
            """)
    Page<MaterialReportCase> findCasesByStatusAssignedTo(@Param("status") MaterialReportCaseStatus status,
                                                         @Param("assignedAdminId") UUID assignedAdminId,
                                                         @Param("from") LocalDateTime from,
                                                         @Param("to") LocalDateTime to,
                                                         Pageable pageable);

    @Query(value = """
            SELECT c FROM MaterialReportCase c
            JOIN FETCH c.academicMaterial m
            JOIN FETCH m.tutorSubject ts
            JOIN FETCH ts.subject s
            JOIN FETCH ts.tutorProfile tp
            JOIN FETCH tp.user tu
            LEFT JOIN FETCH c.assignedAdmin
            WHERE c.openedAt >= :from AND c.openedAt < :to
            """,
            countQuery = """
            SELECT COUNT(c) FROM MaterialReportCase c
            WHERE c.openedAt >= :from AND c.openedAt < :to
            """)
    Page<MaterialReportCase> findCasesAnyStatus(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to,
                                                Pageable pageable);

    @Query(value = """
            SELECT c FROM MaterialReportCase c
            JOIN FETCH c.academicMaterial m
            JOIN FETCH m.tutorSubject ts
            JOIN FETCH ts.subject s
            JOIN FETCH ts.tutorProfile tp
            JOIN FETCH tp.user tu
            JOIN FETCH c.assignedAdmin aa
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND aa.userId = :assignedAdminId
            """,
            countQuery = """
            SELECT COUNT(c) FROM MaterialReportCase c
            JOIN c.assignedAdmin aa
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND aa.userId = :assignedAdminId
            """)
    Page<MaterialReportCase> findCasesAnyStatusAssignedTo(@Param("assignedAdminId") UUID assignedAdminId,
                                                          @Param("from") LocalDateTime from,
                                                          @Param("to") LocalDateTime to,
                                                          Pageable pageable);

    @Query("""
            SELECT c FROM MaterialReportCase c
            JOIN FETCH c.academicMaterial m
            JOIN FETCH m.tutorSubject ts
            JOIN FETCH ts.subject s
            JOIN FETCH ts.tutorProfile tp
            JOIN FETCH tp.user tu
            LEFT JOIN FETCH tp.career
            LEFT JOIN FETCH c.assignedAdmin
            WHERE c.materialReportCaseId = :caseId
            """)
    Optional<MaterialReportCase> findDetailById(@Param("caseId") UUID caseId);

    @Query("""
            SELECT COUNT(c) FROM MaterialReportCase c
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND c.status = :status
            """)
    long countByStatusAndOpenedAt(@Param("status") MaterialReportCaseStatus status,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(c) FROM MaterialReportCase c
            JOIN c.assignedAdmin aa
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND c.status = :status
              AND aa.userId = :assignedAdminId
            """)
    long countByStatusAndOpenedAtAssignedTo(@Param("status") MaterialReportCaseStatus status,
                                            @Param("assignedAdminId") UUID assignedAdminId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MaterialReportCase c
               SET c.status = com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.IN_REVIEW,
                   c.assignedAdmin = :admin,
                   c.assignedAt = :now
             WHERE c.materialReportCaseId = :caseId
               AND c.status = com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.PENDING
            """)
    int assignIfPending(@Param("caseId") UUID caseId,
                        @Param("admin") User admin,
                        @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MaterialReportCase c
               SET c.status = :status,
                   c.resolvedAt = :now,
                   c.resolutionNote = :note
             WHERE c.materialReportCaseId = :caseId
               AND c.status = com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.IN_REVIEW
               AND c.assignedAdmin = :admin
            """)
    int resolveIfInReview(@Param("caseId") UUID caseId,
                          @Param("admin") User admin,
                          @Param("status") MaterialReportCaseStatus status,
                          @Param("now") LocalDateTime now,
                          @Param("note") String note);
}