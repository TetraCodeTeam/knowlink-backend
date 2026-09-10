package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.bookings.controllers.responses.BookingCancellationPreviewResponse;
import com.knowlink.api.bookings.controllers.responses.BookingCancellationResponse;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.data.models.BookingCancellation;
import com.knowlink.api.bookings.events.BookingCancelledEvent;
import com.knowlink.api.bookings.repositories.IBookingCancellationRepository;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.services.RefundPolicyCalculator;
import com.knowlink.api.bookings.services.interfaces.IBookingCancellationService;
import com.knowlink.api.events.services.BookingEventPublisher;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingCancellationServiceImpl implements IBookingCancellationService {

    private static final Logger logger = LoggerFactory.getLogger(BookingCancellationServiceImpl.class);

    private final IBookingRepository bookingRepository;
    private final IBookingCancellationRepository bookingCancellationRepository;
    private final ITimeSlotRepository timeSlotRepository;
    private final RefundPolicyCalculator refundPolicyCalculator;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final BookingEventPublisher bookingEventPublisher;

    @Override
    @Transactional
    public BookingCancellationResponse cancelBooking(UUID bookingId, UUID currentUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND",
                        "La reserva no existe.",
                        "Booking not found for id: " + bookingId));

        validateStatus(booking);
        validateSessionNotPassed(booking);

        CancellationRole cancellationRole = resolveCancellationRole(booking, currentUserId);

        LocalDateTime ahora = LocalDateTime.now(AppTimeZone.ZONE);
        LocalDateTime fechaHoraSesion = LocalDateTime.of(booking.getSessionDate(), booking.getStartTime());

        RefundPolicyCalculator.ResultadoPolitica resultado = refundPolicyCalculator.calcular(
                cancellationRole,
                ahora,
                fechaHoraSesion,
                booking.getAmount());

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        TimeSlot timeSlot = booking.getTimeSlot();
        timeSlot.setStatus(SlotStatus.AVAILABLE);
        timeSlotRepository.save(timeSlot);

        BookingCancellation cancellation = BookingCancellation.builder()
                .booking(booking)
                .cancelledBy(cancellationRole)
                .refundDestination(resultado.refundDestination())
                .refundPolicy(resultado.refundPolicy())
                .amount(resultado.amount())
                .hoursInAdvance(resultado.hoursInAdvance())
                .build();

        bookingCancellationRepository.save(cancellation);

        bookingEventPublisher.publish(
                booking.getTutor().getUserId(),
                timeSlot.getTimeSlotId(),
                "AVAILABLE",
                Instant.now(),
                Instant.now());

        applicationEventPublisher.publishEvent(new BookingCancelledEvent(
                booking.getBookingId(),
                timeSlot.getTimeSlotId(),
                booking.getTutor().getUserId(),
                resultado.refundDestination(),
                resultado.amount()));

        logger.info("Booking {} cancelled by user {} (role: {})", bookingId, currentUserId, cancellationRole);

        return new BookingCancellationResponse(
                booking.getBookingId(),
                booking.getBookingStatus(),
                cancellation.getCancellationId(),
                cancellation.getRefundDestination(),
                cancellation.getRefundPolicy(),
                cancellation.getAmount(),
                cancellation.getHoursInAdvance(),
                cancellation.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public BookingCancellationPreviewResponse previewCancellation(UUID bookingId, UUID currentUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND",
                        "La reserva no existe.",
                        "Booking not found for id: " + bookingId));

        validateStatus(booking);
        validateSessionNotPassed(booking);

        CancellationRole cancellationRole = resolveCancellationRole(booking, currentUserId);

        LocalDateTime ahora = LocalDateTime.now(AppTimeZone.ZONE);
        LocalDateTime fechaHoraSesion = LocalDateTime.of(booking.getSessionDate(), booking.getStartTime());

        RefundPolicyCalculator.ResultadoPolitica resultado = refundPolicyCalculator.calcular(
                cancellationRole,
                ahora,
                fechaHoraSesion,
                booking.getAmount());

        return new BookingCancellationPreviewResponse(
                booking.getBookingId(),
                cancellationRole,
                resultado.hoursInAdvance(),
                resultado.refundDestination(),
                resultado.amount(),
                resultado.refundPolicy());
    }

    private void validateStatus(Booking booking) {
        if (booking.getBookingStatus() != BookingStatus.BOOKED) {
            throw new DuplicateResourceException(
                    "BOOKING_NOT_CANCELLABLE",
                    "Solo se pueden cancelar reservas confirmadas.",
                    "Booking " + booking.getBookingId() + " has status " + booking.getBookingStatus());
        }
    }

    private void validateSessionNotPassed(Booking booking) {
        LocalDateTime fechaHoraSesion = LocalDateTime.of(booking.getSessionDate(), booking.getStartTime());
        if (fechaHoraSesion.isBefore(LocalDateTime.now(AppTimeZone.ZONE))) {
            throw new ValidationException(
                    "No se puede cancelar una reserva cuya sesión ya ocurrió.");
        }
    }

    private CancellationRole resolveCancellationRole(Booking booking, UUID currentUserId) {
        UUID studentId = booking.getStudent().getUserId();
        UUID tutorId = booking.getTutor().getUserId();

        if (currentUserId.equals(studentId)) {
            return CancellationRole.STUDENT;
        }
        if (currentUserId.equals(tutorId)) {
            return CancellationRole.TUTOR;
        }

        throw new ValidationException(
                "No tenés permiso para cancelar esta reserva.");
    }
}
