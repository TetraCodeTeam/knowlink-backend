package com.knowlink.api.payments.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundsLedgerService {

    private final IFundsTransferRepository fundsTransferRepository;

    @Value("")
    private BigDecimal systemRetentionPercentage;

    @Transactional
    public void releaseToTutor(Booking booking, BookingStatus finalStatus) {
        BigDecimal originalAmount = booking.getAmount();
        BigDecimal retentionAmount = originalAmount.multiply(systemRetentionPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal transferredAmount = originalAmount.subtract(retentionAmount);

        FundsTransfer transfer = FundsTransfer.builder()
                .booking(booking)
                .originalAmount(originalAmount)
                .systemRetentionPercentage(systemRetentionPercentage)
                .transferredAmount(transferredAmount)
                .recipient(FundsRecipient.TUTOR)
                .concept("Liberacion por sesion completada")
                .fundsStatus(FundsStatus.RELEASED_TO_TUTOR)
                .processedAt(Instant.now())
                .build();

        fundsTransferRepository.save(transfer);
        log.info("Funds released to tutor for booking {}: {} (retention: {})",
                booking.getBookingId(), transferredAmount, retentionAmount);
    }

    @Transactional
    public void refundToStudent(Booking booking, BookingStatus finalStatus) {
        BigDecimal originalAmount = booking.getAmount();

        FundsTransfer transfer = FundsTransfer.builder()
                .booking(booking)
                .originalAmount(originalAmount)
                .systemRetentionPercentage(BigDecimal.ZERO)
                .transferredAmount(originalAmount)
                .recipient(FundsRecipient.STUDENT)
                .concept(finalStatus == BookingStatus.NOT_FULFILLED_BY_TUTOR
                        ? "Devolucion por tutor ausente"
                        : "Devolucion por sesion no realizada")
                .fundsStatus(FundsStatus.REFUNDED_TO_STUDENT)
                .processedAt(Instant.now())
                .build();

        fundsTransferRepository.save(transfer);
        log.info("Funds refunded to student for booking {}: {}",
                booking.getBookingId(), originalAmount);
    }

    @Transactional
    public void markSuspendedByClaim(UUID bookingId, UUID claimId) {
        fundsTransferRepository.findByBooking_BookingId(bookingId)
                .ifPresent(transfer -> {
                    transfer.setFundsStatus(FundsStatus.SUSPENDED_BY_CLAIM);
                    transfer.setBlockingClaimId(claimId);
                    fundsTransferRepository.save(transfer);
                    log.info("Funds suspended by claim {} for booking {}", claimId, bookingId);
                });
    }

    @Transactional
    public void createHeldTransfer(Booking booking) {
        FundsTransfer transfer = FundsTransfer.builder()
                .booking(booking)
                .originalAmount(booking.getAmount())
                .systemRetentionPercentage(systemRetentionPercentage)
                .transferredAmount(BigDecimal.ZERO)
                .recipient(FundsRecipient.TUTOR)
                .concept("Fondos retenidos para reserva")
                .fundsStatus(FundsStatus.HELD)
                .build();

        fundsTransferRepository.save(transfer);
        log.info("Held funds created for booking {}: {}", booking.getBookingId(), booking.getAmount());
    }
}