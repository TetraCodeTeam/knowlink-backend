package com.knowlink.api.bookings.services.interfaces;

import com.knowlink.api.bookings.controllers.responses.BookingCalendarResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface IBookingCalendarService {
    BookingCalendarResponse getCalendar(UUID tutorUserId, LocalDate from, LocalDate to);
}
