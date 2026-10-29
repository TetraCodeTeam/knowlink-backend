package com.knowlink.api.admin.materialreports.repositories;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read port consumed by the admin dashboard (US-33). Every metric is aggregated in the database.
 */
public interface IMaterialReportMetricsRepository extends JpaRepository<MaterialReportCase, UUID> {

    @Query("""
            SELECT COUNT(c) FROM MaterialReportCase c
            WHERE c.status = com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.PENDING
            """)
    long countPending();

    @Query("""
            SELECT COUNT(c) FROM MaterialReportCase c
            WHERE c.status IN (com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.REMOVED,
                               com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.DISMISSED)
              AND c.resolvedAt >= :from AND c.resolvedAt < :to
            """)
    long countResolvedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(c) FROM MaterialReportCase c
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND c.status = :status
            """)
    long countOpenedBetweenWithStatus(@Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      @Param("status") MaterialReportCaseStatus status);

    /**
     * Most reported pending case; ties are resolved by the oldest opened case.
     */
    default Optional<MaterialReportCase> findMostReportedPending() {
        return findMostReportedPendingLimited(PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("""
            SELECT c FROM MaterialReportCase c
            JOIN FETCH c.academicMaterial
            WHERE c.status = com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.PENDING
            ORDER BY c.reportsCount DESC, c.openedAt ASC
            """)
    List<MaterialReportCase> findMostReportedPendingLimited(Pageable pageable);

    /**
     * Average seconds between opening and resolution for cases resolved in the window.
     */
    @Query("""
            SELECT AVG(timestampdiff(second, c.openedAt, c.resolvedAt)) FROM MaterialReportCase c
            WHERE c.status IN (com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.REMOVED,
                               com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.DISMISSED)
              AND c.resolvedAt >= :from AND c.resolvedAt < :to
            """)
    Double avgResolutionSeconds(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(c) FROM MaterialReportCase c
            WHERE c.openedAt >= :from AND c.openedAt < :to
            """)
    long countOpenedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(c) FROM MaterialReportCase c
            WHERE c.openedAt >= :from AND c.openedAt < :to
              AND c.status IN (com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.PENDING,
                               com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.IN_REVIEW)
            """)
    long countOpenedBetweenUnresolved(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}