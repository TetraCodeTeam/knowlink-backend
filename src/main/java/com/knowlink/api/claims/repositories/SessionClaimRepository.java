package com.knowlink.api.claims.repositories;

import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SessionClaimRepository extends JpaRepository<SessionClaim, UUID> {

    boolean existsByBooking_BookingIdAndClaimant_UserIdAndStatus(UUID bookingId, UUID userId, ClaimStatus status);

    boolean existsByBooking_BookingIdAndStatus(UUID bookingId, ClaimStatus status);

    Optional<SessionClaim> findByClaimIdAndBooking_BookingId(UUID claimId, UUID bookingId);

    @Query("""
                    SELECT c.booking.bookingId FROM SessionClaim c
                    WHERE c.claimant.userId = :userId
                    AND c.status = :status
                    AND c.booking.bookingId IN :bookingIds
                    """)
    Set<UUID> findClaimedBookingIdsByUser(@Param("userId") UUID userId, @Param("status") ClaimStatus status,
            @Param("bookingIds") Collection<UUID> bookingIds);
}