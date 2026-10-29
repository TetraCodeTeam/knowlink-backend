package com.knowlink.api.claims.repositories;

import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SessionClaimRepository extends JpaRepository<SessionClaim, UUID> {

    boolean existsByBooking_BookingIdAndClaimant_UserIdAndStatusIn(
            UUID bookingId, UUID userId, Collection<ClaimStatus> statuses);

    boolean existsByBooking_BookingIdAndClaimant_UserIdAndStatus(
            UUID bookingId, UUID userId, ClaimStatus status);

    boolean existsByBooking_BookingIdAndStatusIn(UUID bookingId, Collection<ClaimStatus> statuses);

    boolean existsByBooking_BookingIdAndStatus(UUID bookingId, ClaimStatus status);

    Optional<SessionClaim> findFirstByBooking_BookingIdAndStatusInOrderByCreatedAtAsc(
            UUID bookingId, Collection<ClaimStatus> statuses);

    Optional<SessionClaim> findByClaimIdAndBooking_BookingId(UUID claimId, UUID bookingId);

    @Query("""
            SELECT claim FROM SessionClaim claim
            JOIN FETCH claim.booking booking
            JOIN FETCH booking.tutorSubject tutorSubject
            JOIN FETCH tutorSubject.subject subject
            JOIN FETCH subject.career
            JOIN FETCH booking.student
            JOIN FETCH claim.claimant
            WHERE claim.claimId = :claimId AND booking.tutor.userId = :tutorUserId
            """)
    Optional<SessionClaim> findByClaimIdAndTutorUserId(
            @Param("claimId") UUID claimId,
            @Param("tutorUserId") UUID tutorUserId);

    @Query("""
            SELECT COUNT(claim) FROM SessionClaim claim
            WHERE claim.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN
              AND (claim.createdAt < :createdAt
                   OR (claim.createdAt = :createdAt AND claim.claimId <= :claimId))
            """)
    long countOpenClaimsInQueueThrough(
            @Param("createdAt") LocalDateTime createdAt,
            @Param("claimId") UUID claimId);

    @Query("""
            SELECT claim FROM SessionClaim claim
            JOIN FETCH claim.booking booking
            JOIN FETCH booking.tutor
            JOIN FETCH booking.tutorSubject tutorSubject
            JOIN FETCH tutorSubject.subject subject
            JOIN FETCH subject.career
            WHERE booking.student.userId = :studentUserId
            ORDER BY claim.createdAt DESC
            """)
    List<SessionClaim> findStudentClaims(@Param("studentUserId") UUID studentUserId);

    @Query("""
            SELECT claim FROM SessionClaim claim
            JOIN FETCH claim.booking booking
            JOIN FETCH booking.student
            JOIN FETCH booking.tutor
            JOIN FETCH booking.tutorSubject tutorSubject
            JOIN FETCH tutorSubject.subject subject
            JOIN FETCH subject.career
            JOIN FETCH claim.claimant
            WHERE claim.claimId = :claimId
              AND booking.student.userId = :studentUserId
            """)
    Optional<SessionClaim> findStudentClaimDetails(
            @Param("claimId") UUID claimId,
            @Param("studentUserId") UUID studentUserId);

    @Query("""
            SELECT claim FROM SessionClaim claim
            JOIN FETCH claim.booking booking
            JOIN FETCH booking.tutorSubject tutorSubject
            JOIN FETCH tutorSubject.subject
            JOIN FETCH booking.student
            WHERE booking.tutor.userId = :tutorUserId
            ORDER BY claim.createdAt DESC
            """)
    List<SessionClaim> findByBooking_Tutor_UserIdOrderByCreatedAtDesc(@Param("tutorUserId") UUID tutorUserId);

    @Query("""
                    SELECT c.booking.bookingId FROM SessionClaim c
                    WHERE c.claimant.userId = :userId
                    AND c.status IN :statuses
                    AND c.booking.bookingId IN :bookingIds
                    """)
    Set<UUID> findClaimedBookingIdsByUser(
            @Param("userId") UUID userId, @Param("statuses") Collection<ClaimStatus> statuses,
            @Param("bookingIds") Collection<UUID> bookingIds);

    @Query("""
            SELECT c.booking.bookingId FROM SessionClaim c
            WHERE c.claimant.userId = :userId
            AND c.status = :status
            AND c.booking.bookingId IN :bookingIds
            """)
    Set<UUID> findClaimedBookingIdsByUser(
            @Param("userId") UUID userId, @Param("status") ClaimStatus status,
            @Param("bookingIds") Collection<UUID> bookingIds);
}
