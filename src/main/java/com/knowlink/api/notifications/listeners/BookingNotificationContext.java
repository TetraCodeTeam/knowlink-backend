package com.knowlink.api.notifications.listeners;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Re-reads a booking inside a read-only transaction so listeners running after commit
 * never touch lazy associations with the persistence context already closed.
 */
@Component
@RequiredArgsConstructor
public class BookingNotificationContext {

    private final IBookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public Optional<BookingSnapshot> loadSnapshot(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(BookingNotificationContext::toSnapshot);
    }

    private static BookingSnapshot toSnapshot(Booking booking) {
        return new BookingSnapshot(
                booking.getBookingId(),
                booking.getStudent().getUserId(),
                booking.getTutor().getUserId(),
                booking.getStudent().getFullName(),
                booking.getTutor().getFullName(),
                resolveSubjectName(booking),
                booking.getSessionDate(),
                booking.getStartTime());
    }

    private static String resolveSubjectName(Booking booking) {
        if (booking.getTutorSubject() == null || booking.getTutorSubject().getSubject() == null) {
            return null;
        }
        return booking.getTutorSubject().getSubject().getName();
    }
}