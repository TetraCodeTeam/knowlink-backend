package com.knowlink.api.payments.repositories;

import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IFundsTransferRepository extends JpaRepository<FundsTransfer, UUID> {

    Optional<FundsTransfer> findByBooking_BookingId(UUID bookingId);

    boolean existsByBooking_BookingIdAndFundsStatusIn(UUID bookingId, List<FundsStatus> statuses);

    Optional<FundsTransfer> findByBooking_BookingIdAndFundsStatus(UUID bookingId, FundsStatus fundsStatus);
}