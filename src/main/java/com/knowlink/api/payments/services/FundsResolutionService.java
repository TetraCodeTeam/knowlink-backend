package com.knowlink.api.payments.services;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.FundsExecutionException;
import com.knowlink.api.exceptions.custom_exceptions.ImmutableBookingException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.payments.controllers.responses.FundsTransferResponseDTO;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundsResolutionService {

    private static final String FUNDS_STATE_MISMATCH = "FUNDS_STATE_MISMATCH";
    private static final String FUNDS_STATE_MISMATCH_MESSAGE =
            "El estado de los fondos no permite resolver el reclamo.";
    private static final String CLAIM_STILL_OPEN = "CLAIM_STILL_OPEN";
    private static final String CLAIM_STILL_OPEN_MESSAGE =
            "La reserva tiene un reclamo abierto; resolvelo desde el panel de reclamos.";
    private static final String CONCEPT_CLAIM_FAVORED_TUTOR =
            "Liberación por resolución de reclamo a favor del tutor";
    private static final String CONCEPT_CLAIM_FAVORED_STUDENT =
            "Devolución por resolución de reclamo a favor del alumno";

    private final IBookingRepository bookingRepository;
    private final SessionClaimRepository sessionClaimRepository;
    private final IFundsTransferRepository fundsTransferRepository;
    private final FundsLedgerService fundsLedgerService;

    @Transactional
    public void resolve(UUID bookingId, boolean tutorPresent, boolean studentPresent) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND",
                        "La reserva no existe.",
                        "Booking not found for id: " + bookingId));

        if (sessionClaimRepository.existsByBooking_BookingIdAndStatusIn(
                bookingId, ClaimStatus.ACTIVE_STATUSES)) {
            log.info("Booking {} has an active claim, funds resolution skipped", bookingId);
            return;
        }

        Optional<FundsTransfer> existingTransfer = fundsTransferRepository.findByBooking_BookingId(bookingId);
        if (existingTransfer.isPresent() && isTerminalStatus(existingTransfer.get().getFundsStatus())) {
            throw new ImmutableBookingException(bookingId);
        }

        if (fundsTransferRepository.existsByBooking_BookingIdAndFundsStatusIn(
                bookingId, List.of(FundsStatus.SUSPENDED_BY_CLAIM))) {
            log.info("Booking {} has active claim, funds resolution suspended", bookingId);
            return;
        }

        if (tutorPresent) {
            booking.setBookingStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
            fundsLedgerService.releaseToTutor(booking, BookingStatus.COMPLETED, studentPresent);
        } else {
            BookingStatus finalStatus = studentPresent
                    ? BookingStatus.NOT_FULFILLED_BY_TUTOR
                    : BookingStatus.SESSION_NOT_HELD;
            booking.setBookingStatus(finalStatus);
            bookingRepository.save(booking);
            fundsLedgerService.refundToStudent(booking, finalStatus);
        }

        log.info("Funds resolved for booking {}: tutorPresent={}, studentPresent={}",
                bookingId, tutorPresent, studentPresent);
    }

    /**
     * Applies the explicit administrator decision over the funds row already held for the booking.
     * The existing ledger row is updated (never inserted) to respect the unique constraint per booking.
     *
     * @return the amount effectively moved; {@code BigDecimal.ZERO} for free sessions
     */
    @Transactional
    public BigDecimal resolveByAdminDecision(Booking booking, ClaimFavoredParty favoredParty, UUID claimId) {
        UUID bookingId = booking.getBookingId();
        try {
            Optional<FundsTransfer> existingTransfer = fundsTransferRepository.findByBooking_BookingId(bookingId);
            if (existingTransfer.isEmpty()) {
                log.info("Booking {} has no funds transfer (free session), nothing to move", bookingId);
                return BigDecimal.ZERO;
            }

            FundsTransfer transfer = existingTransfer.get();
            if (isTerminalStatus(transfer.getFundsStatus())) {
                throw new ImmutableBookingException(bookingId);
            }
            if (transfer.getFundsStatus() != FundsStatus.SUSPENDED_BY_CLAIM
                    || !claimId.equals(transfer.getBlockingClaimId())) {
                throw new BusinessConflictException(
                        FUNDS_STATE_MISMATCH,
                        FUNDS_STATE_MISMATCH_MESSAGE,
                        "Funds state mismatch for booking " + bookingId + ": status=" + transfer.getFundsStatus()
                                + ", blockingClaimId=" + transfer.getBlockingClaimId() + ", claimId=" + claimId);
            }

            BigDecimal originalAmount = transfer.getOriginalAmount() != null
                    ? transfer.getOriginalAmount()
                    : booking.getAmount();
            BigDecimal transferredAmount;

            if (favoredParty == ClaimFavoredParty.TUTOR) {
                transferredAmount = fundsLedgerService.calculateTutorTransferredAmount(originalAmount);
                transfer.setRecipient(FundsRecipient.TUTOR);
                transfer.setTransferredAmount(transferredAmount);
                transfer.setFundsStatus(FundsStatus.RELEASED_TO_TUTOR);
                transfer.setConcept(CONCEPT_CLAIM_FAVORED_TUTOR);
            } else {
                transferredAmount = originalAmount;
                transfer.setRecipient(FundsRecipient.STUDENT);
                transfer.setSystemRetentionPercentage(BigDecimal.ZERO);
                transfer.setTransferredAmount(originalAmount);
                transfer.setFundsStatus(FundsStatus.REFUNDED_TO_STUDENT);
                transfer.setConcept(CONCEPT_CLAIM_FAVORED_STUDENT);
            }

            // blockingClaimId is kept as an audit reference of the claim that suspended the funds
            transfer.setProcessedAt(Instant.now());
            fundsTransferRepository.save(transfer);

            log.info("Claim {} resolved in favor of {}: booking {} moved {}",
                    claimId, favoredParty, bookingId, transferredAmount);
            return transferredAmount;
        } catch (BusinessConflictException | ImmutableBookingException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new FundsExecutionException(
                    "Failed to execute the funds decision of claim " + claimId + " for booking " + bookingId, ex);
        }
    }

    @Transactional
    public void retryAfterClaimResolution(UUID bookingId) {
        if (sessionClaimRepository.existsByBooking_BookingIdAndStatusIn(
                bookingId, ClaimStatus.ACTIVE_STATUSES)) {
            throw new BusinessConflictException(
                    CLAIM_STILL_OPEN,
                    CLAIM_STILL_OPEN_MESSAGE,
                    "Booking " + bookingId + " still has an open or in-review claim");
        }

        Optional<FundsTransfer> suspendedTransfer = fundsTransferRepository
                .findByBooking_BookingIdAndFundsStatus(bookingId, FundsStatus.SUSPENDED_BY_CLAIM);

        if (suspendedTransfer.isEmpty()) {
            log.warn("No suspended transfer found for booking {}", bookingId);
            return;
        }

        FundsTransfer transfer = suspendedTransfer.get();
        transfer.setFundsStatus(FundsStatus.HELD);
        transfer.setBlockingClaimId(null);
        fundsTransferRepository.save(transfer);

        log.info("Funds transfer for booking {} unblocked after claim resolution", bookingId);
    }

    @Transactional(readOnly = true)
    public Optional<FundsTransferResponseDTO> getFundsStatus(UUID bookingId) {
        return fundsTransferRepository.findByBooking_BookingId(bookingId)
                .map(this::toResponseDTO);
    }

    private boolean isTerminalStatus(FundsStatus status) {
        return status == FundsStatus.RELEASED_TO_TUTOR || status == FundsStatus.REFUNDED_TO_STUDENT;
    }

    private FundsTransferResponseDTO toResponseDTO(FundsTransfer transfer) {
        return new FundsTransferResponseDTO(
                transfer.getFundsTransferId(),
                transfer.getBooking().getBookingId(),
                transfer.getOriginalAmount(),
                transfer.getSystemRetentionPercentage(),
                transfer.getTransferredAmount(),
                transfer.getRecipient(),
                transfer.getConcept(),
                transfer.getFundsStatus(),
                transfer.getProcessedAt(),
                transfer.getBlockingClaimId());
    }
}