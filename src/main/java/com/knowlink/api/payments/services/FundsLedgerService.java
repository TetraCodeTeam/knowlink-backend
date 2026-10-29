package com.knowlink.api.payments.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsConcept;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.events.FundsSuspendedByClaimEvent;
import com.knowlink.api.payments.events.FundsTransferProcessedEvent;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${knowlink.funds.retention-percentage:3.00}")
    private BigDecimal systemRetentionPercentage;

    @Transactional
    public void releaseToTutor(Booking booking, BookingStatus finalStatus, boolean studentPresent) {
        BigDecimal originalAmount = booking.getAmount();
        BigDecimal retentionAmount = originalAmount.multiply(systemRetentionPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal transferredAmount = originalAmount.subtract(retentionAmount);
        FundsConcept concept = studentPresent
                ? FundsConcept.RELEASED_SESSION_COMPLETED
                : FundsConcept.RELEASED_STUDENT_ABSENT;

        FundsTransfer transfer = findOrCreateRow(booking);
        transfer.setOriginalAmount(originalAmount);
        transfer.setSystemRetentionPercentage(systemRetentionPercentage);
        transfer.setTransferredAmount(transferredAmount);
        transfer.setRecipient(FundsRecipient.TUTOR);
        transfer.setConcept(concept.label());
        transfer.setFundsStatus(FundsStatus.RELEASED_TO_TUTOR);
        transfer.setProcessedAt(Instant.now());
        transfer.setBlockingClaimId(null);
        fundsTransferRepository.save(transfer);
        applicationEventPublisher.publishEvent(new FundsTransferProcessedEvent(
                booking.getBookingId(),
                FundsRecipient.TUTOR,
                originalAmount,
                transferredAmount,
                systemRetentionPercentage,
                concept));
        log.info("Funds released to tutor for booking {}: {} (retention: {})",
                booking.getBookingId(), transferredAmount, retentionAmount);
    }

    @Transactional
    public void refundToStudent(Booking booking, BookingStatus finalStatus) {
        BigDecimal originalAmount = booking.getAmount();

        FundsConcept concept = finalStatus == BookingStatus.NOT_FULFILLED_BY_TUTOR
                ? FundsConcept.REFUNDED_TUTOR_ABSENT
                : FundsConcept.REFUNDED_SESSION_NOT_HELD;

        FundsTransfer transfer = findOrCreateRow(booking);
        transfer.setOriginalAmount(originalAmount);
        transfer.setSystemRetentionPercentage(BigDecimal.ZERO);
        transfer.setTransferredAmount(originalAmount);
        transfer.setRecipient(FundsRecipient.STUDENT);
        transfer.setConcept(concept.label());
        transfer.setFundsStatus(FundsStatus.REFUNDED_TO_STUDENT);
        transfer.setProcessedAt(Instant.now());
        transfer.setBlockingClaimId(null);
        fundsTransferRepository.save(transfer);
        applicationEventPublisher.publishEvent(new FundsTransferProcessedEvent(
                booking.getBookingId(),
                FundsRecipient.STUDENT,
                originalAmount,
                originalAmount,
                null,
                concept));
        log.info("Funds refunded to student for booking {}: {}",
                booking.getBookingId(), originalAmount);
    }

    @Transactional
    public void markSuspendedByClaim(UUID bookingId, UUID claimId) {
        fundsTransferRepository.findByBooking_BookingId(bookingId)
                .ifPresent(transfer -> {
                    if (transfer.getFundsStatus() == FundsStatus.SUSPENDED_BY_CLAIM) {
                        log.info("Funds for booking {} are already suspended by claim {}, keeping original block",
                                bookingId, transfer.getBlockingClaimId());
                        return;
                    }
                    transfer.setFundsStatus(FundsStatus.SUSPENDED_BY_CLAIM);
                    transfer.setBlockingClaimId(claimId);
                    fundsTransferRepository.save(transfer);
                    applicationEventPublisher.publishEvent(new FundsSuspendedByClaimEvent(bookingId, claimId));
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
                .concept(FundsConcept.HELD.label())
                .fundsStatus(FundsStatus.HELD)
                .build();

        fundsTransferRepository.save(transfer);
        applicationEventPublisher.publishEvent(new FundsTransferProcessedEvent(
                booking.getBookingId(),
                FundsRecipient.TUTOR,
                booking.getAmount(),
                BigDecimal.ZERO,
                null,
                FundsConcept.HELD));
        log.info("Held funds created for booking {}: {}", booking.getBookingId(), booking.getAmount());
    }

    /**
     * Amount the tutor receives for an amount held in the ledger, after the configured retention.
     */
    public BigDecimal calculateTutorTransferredAmount(BigDecimal originalAmount) {
        if (originalAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal retentionAmount = originalAmount.multiply(systemRetentionPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return originalAmount.subtract(retentionAmount);
    }


    /**
     * The ledger keeps a single row per booking that evolves through states
     * (HELD -> SUSPENDED_BY_CLAIM -> HELD -> RELEASED/REFUNDED). Rows created before
     * this lifecycle (or free sessions resolved without a prior hold) fall back to insert.
     */
    private FundsTransfer findOrCreateRow(Booking booking) {
        return fundsTransferRepository.findByBooking_BookingId(booking.getBookingId())
                .orElseGet(() -> FundsTransfer.builder()
                        .booking(booking)
                        .build());
    }
}