package com.knowlink.api.claims.utils;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

@Component
public class ClaimDeadlineCalculator {

    /**
     * Estados de una sesión que no fue confirmada con token y puede ser objeto de un reclamo de asistencia.
     * Las canceladas, vencidas o ya resueltas por fondos quedan afuera.
     */
    private static final Set<BookingStatus> CLAIMABLE_STATUSES = Set.of(
            BookingStatus.BOOKED, BookingStatus.IN_PROGRESS, BookingStatus.NOT_CONFIRMED);

    private final Clock clock;
    private final long deadlineHours;

    public ClaimDeadlineCalculator(Clock clock,
            @Value("${knowlink.claims.deadline-hours:24}") long deadlineHours) {
        this.clock = clock;
        this.deadlineHours = deadlineHours;
    }

    /** Si ambos asistieron (token ingresado), el reclamo por asistencia no tiene sentido. */
    public boolean isConfirmed(Booking booking) {
        return booking.getConfirmedAt() != null || booking.getBookingStatus() == BookingStatus.COMPLETED;
    }

    public boolean isClaimableStatus(Booking booking) {
        return CLAIMABLE_STATUSES.contains(booking.getBookingStatus());
    }

    /** La sesión terminó por horario programado, sin importar su estado. */
    public boolean hasEnded(Booking booking) {
        return !LocalDateTime.now(clock).isBefore(sessionEnd(booking));
    }

    /** El plazo se cuenta desde la hora de fin programada: una sesión sin confirmar no tiene otro evento de cierre. */
    public LocalDateTime deadline(Booking booking) {
        return sessionEnd(booking).plusHours(deadlineHours);
    }

    public boolean isWithinDeadline(Booking booking) {
        return !LocalDateTime.now(clock).isAfter(deadline(booking));
    }

    private LocalDateTime sessionEnd(Booking booking) {
        return LocalDateTime.of(booking.getSessionDate(), booking.getEndTime());
    }
}
