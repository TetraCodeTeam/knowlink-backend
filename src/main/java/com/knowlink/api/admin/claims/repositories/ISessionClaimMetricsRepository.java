package com.knowlink.api.admin.claims.repositories;

import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read port for the admin dashboard (US-33). Every metric counts only the representative claim of
 * each dispute, so a booking with two claims is counted once.
 */
public interface ISessionClaimMetricsRepository extends JpaRepository<SessionClaim, UUID> {

    @Query("""
            SELECT COUNT(c) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN
            """)
    long countOpen();

    @Query("""
            SELECT COUNT(c) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status = com.knowlink.api.claims.data.enums.ClaimStatus.RESOLVED
              AND c.resolvedAt >= :from AND c.resolvedAt < :to
            """)
    long countResolvedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(c) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status = :status
              AND c.createdAt >= :from AND c.createdAt < :to
            """)
    long countOpenedBetweenWithStatus(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") ClaimStatus status);

    @Query("""
            SELECT MIN(c.createdAt) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN
            """)
    LocalDateTime findOldestPendingOpenedAt();

    /**
     * Average seconds between opening and resolution for disputes resolved in the window.
     */
    @Query("""
            SELECT AVG(timestampdiff(second, c.createdAt, c.resolvedAt)) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.status = com.knowlink.api.claims.data.enums.ClaimStatus.RESOLVED
              AND c.resolvedAt >= :from AND c.resolvedAt < :to
            """)
    Double avgResolutionSeconds(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(c) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.createdAt >= :from AND c.createdAt < :to
            """)
    long countOpenedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(c) FROM SessionClaim c
            WHERE (c.disputeId IS NULL OR c.claimId = c.disputeId)
              AND c.createdAt >= :from AND c.createdAt < :to
              AND (c.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN
                   OR c.status = com.knowlink.api.claims.data.enums.ClaimStatus.IN_REVIEW)
            """)
    long countOpenedBetweenUnresolved(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
