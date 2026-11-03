package com.knowlink.api.payments.repositories;

import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IFundsTransferRepository extends JpaRepository<FundsTransfer, UUID> {

    Optional<FundsTransfer> findByBooking_BookingId(UUID bookingId);

    boolean existsByBooking_BookingIdAndFundsStatusIn(UUID bookingId, List<FundsStatus> statuses);

    Optional<FundsTransfer> findByBooking_BookingIdAndFundsStatus(UUID bookingId, FundsStatus fundsStatus);

    @Query("""
            SELECT CASE WHEN COUNT(transfer) > 0 THEN true ELSE false END
            FROM FundsTransfer transfer
            WHERE transfer.fundsStatus IN :statuses
              AND (transfer.booking.student.userId = :userId
                   OR transfer.booking.tutor.userId = :userId)
            """)
    boolean existsRetainedFundsForUser(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<FundsStatus> statuses);
}