package com.knowlink.api.admin.claims.repositories;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.users.data.models.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin queries over session claims. A dispute groups every claim of the same booking, so the
 * representative claim (the one whose claimId is the disputeId) is the only one listed and the
 * mutations are applied to the whole dispute.
 */
public interface IAdminSessionClaimRepository extends JpaRepository<SessionClaim, UUID> {


    @Query(value = """
            SELECT c FROM SessionClaim c
            JOIN FETCH c.booking b
            JOIN FETCH b.student
            JOIN FETCH b.tutor
            JOIN FETCH b.tutorSubject ts
            JOIN FETCH ts.subject
            LEFT JOIN FETCH c.assignedAdmin
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status IN :statuses
              AND c.createdAt >= :from AND c.createdAt < :to
            ORDER BY CASE WHEN c.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN THEN 0
                          WHEN c.status = com.knowlink.api.claims.data.enums.ClaimStatus.IN_REVIEW THEN 1
                          ELSE 2 END ASC,
                     c.createdAt ASC
            """, countQuery = """
            SELECT COUNT(c) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status IN :statuses
              AND c.createdAt >= :from AND c.createdAt < :to
            """)
    Page<SessionClaim> findAdminPage(
            @Param("statuses") Collection<ClaimStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query(value = """
            SELECT c FROM SessionClaim c
            JOIN FETCH c.booking b
            JOIN FETCH b.student
            JOIN FETCH b.tutor
            JOIN FETCH b.tutorSubject ts
            JOIN FETCH ts.subject
            JOIN FETCH c.assignedAdmin aa
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status IN :statuses
              AND c.createdAt >= :from AND c.createdAt < :to
              AND aa.userId = :adminUserId
            ORDER BY CASE WHEN c.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN THEN 0
                          WHEN c.status = com.knowlink.api.claims.data.enums.ClaimStatus.IN_REVIEW THEN 1
                          ELSE 2 END ASC,
                     c.createdAt ASC
            """, countQuery = """
            SELECT COUNT(c) FROM SessionClaim c
            JOIN c.assignedAdmin aa
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status IN :statuses
              AND c.createdAt >= :from AND c.createdAt < :to
              AND aa.userId = :adminUserId
            """)
    Page<SessionClaim> findAdminPageAssignedTo(
            @Param("statuses") Collection<ClaimStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("adminUserId") UUID adminUserId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(c) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status = :status
              AND c.createdAt >= :from AND c.createdAt < :to
            """)
    long countAdminByStatus(
            @Param("status") ClaimStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(c) FROM SessionClaim c
            JOIN c.assignedAdmin aa
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status = :status
              AND c.createdAt >= :from AND c.createdAt < :to
              AND aa.userId = :adminUserId
            """)
    long countAdminByStatusAssignedTo(
            @Param("status") ClaimStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("adminUserId") UUID adminUserId);

    @Query("""
            SELECT c FROM SessionClaim c
            JOIN FETCH c.booking b
            JOIN FETCH b.student
            JOIN FETCH b.tutor
            JOIN FETCH b.tutorSubject ts
            JOIN FETCH ts.subject
            LEFT JOIN FETCH c.assignedAdmin
            WHERE c.claimId = :claimId
            """)
    Optional<SessionClaim> findByIdWithDetails(@Param("claimId") UUID claimId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SessionClaim c WHERE c.claimId = :claimId")
    Optional<SessionClaim> findByIdForUpdate(@Param("claimId") UUID claimId);

    @Query("""
            SELECT c FROM SessionClaim c
            JOIN FETCH c.claimant
            WHERE (c.disputeId = :disputeKey OR c.claimId = :disputeKey)
            ORDER BY c.createdAt ASC
            """)
    List<SessionClaim> findDisputeClaims(@Param("disputeKey") UUID disputeKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SessionClaim c
               SET c.status = com.knowlink.api.claims.data.enums.ClaimStatus.IN_REVIEW,
                   c.assignedAdmin = :admin,
                   c.assignedAt = :now,
                   c.updatedAt = :now
             WHERE (c.disputeId = :disputeKey OR c.claimId = :disputeKey)
               AND c.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN
            """)
    int assignIfOpen(
            @Param("disputeKey") UUID disputeKey,
            @Param("admin") User admin,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SessionClaim c
               SET c.status = com.knowlink.api.claims.data.enums.ClaimStatus.RESOLVED,
                   c.resolvedAt = :now,
                   c.favoredParty = :favoredParty,
                   c.resolutionJustification = :justification,
                   c.activeKey = NULL,
                   c.updatedAt = :now
             WHERE (c.disputeId = :disputeKey OR c.claimId = :disputeKey)
            """)
    int resolveDispute(
            @Param("disputeKey") UUID disputeKey,
            @Param("favoredParty") ClaimFavoredParty favoredParty,
            @Param("justification") String justification,
            @Param("now") LocalDateTime now);
}