package com.knowlink.api.bookings.controllers.responses;

import java.util.List;

public record BookingCalendarResponse(
        Integer minimumNoticeMinutes,
        List<BookingSlotResponse> slots
) {}
