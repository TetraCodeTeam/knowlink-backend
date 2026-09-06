package com.knowlink.api.tutors.schedule.controllers.responses;

import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;

import java.time.LocalDate;
import java.util.List;

public record WeeklyScheduleResponse(
        LocalDate from,
        LocalDate to,
        List<AvailabilityBlockResponse> availabilityBlocks,
        List<BookingResponse> bookings,
        WeeklyScheduleSummaryResponse summary
) {}
