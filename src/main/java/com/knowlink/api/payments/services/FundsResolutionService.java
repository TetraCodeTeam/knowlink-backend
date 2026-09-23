package com.knowlink.api.payments.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.exceptions.custom_exceptions.ImmutableBookingException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.payments.controllers.responses.FundsTransferResponseDTO;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundsResolutionService {

    private final IBookingRepository bookingRepository;
    private final IFundsTransferRepository fundsTransferRepository;
    private final FundsLedgerService fundsLedgerService;

    @Transactional
    public void resolve(UUID bookingId, boolean tutorPresent, boolean studentPresent) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND",
                        "La reserva no existe.",
                        "Booking not found for id: " + bookingId));

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
            fundsLedgerService.releaseToTutor(booking, BookingStatus.COMPLETED);
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

    @Transactional
    public void retryAfterClaimResolution(UUID bookingId) {
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