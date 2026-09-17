package com.knowlink.api.bookings.events;

import com.knowlink.api.bookings.data.models.Booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingConfirmationEventPayload(
        UUID bookingId,
        UUID studentUserId,
        String studentFullName,
        UUID tutorUserId,
        String tutorFullName,
        String subjectName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime
) {
    public static BookingConfirmationEventPayload from(Booking booking) {
        return new BookingConfirmationEventPayload(
                booking.getBookingId(),
                booking.getStudent().getUserId(),
                booking.getStudent().getFullName(),
                booking.getTutor().getUserId(),
                booking.getTutor().getFullName(),
                booking.getTutorSubject().getSubject().getName(),
                booking.getSessionDate(),
                booking.getStartTime(),
                booking.getEndTime());
    }
}