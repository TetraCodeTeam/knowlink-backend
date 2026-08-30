package com.knowlink.api.bookings.controllers.implementations;

import com.knowlink.api.bookings.controllers.interfaces.IBookingCalendarController;
import com.knowlink.api.bookings.controllers.responses.BookingCalendarResponse;
import com.knowlink.api.bookings.services.interfaces.IBookingCalendarService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BookingCalendarControllerImpl implements IBookingCalendarController {

    private final IBookingCalendarService bookingCalendarService;

    @Override
    public BookingCalendarResponse getCalendar(UUID tutorId, LocalDate from, LocalDate to) {
        return bookingCalendarService.getCalendar(tutorId, from, to);
    }
}
