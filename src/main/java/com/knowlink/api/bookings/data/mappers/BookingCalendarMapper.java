package com.knowlink.api.bookings.data.mappers;

import com.knowlink.api.bookings.controllers.responses.BookingSlotResponse;
import com.knowlink.api.bookings.controllers.responses.BookingSlotUnavailableWindowResponse;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.timeslot.data.models.TimeSlot;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class BookingCalendarMapper {

    public BookingSlotUnavailableWindowResponse toWindowResponse(
            LocalDate date, LocalTime start, LocalTime end, String status) {
        return new BookingSlotUnavailableWindowResponse(
                date.atTime(start).atZone(AppTimeZone.ZONE).toInstant(),
                date.atTime(end).atZone(AppTimeZone.ZONE).toInstant(),
                status);
    }

    public BookingSlotResponse toSlotResponse(
            TimeSlot slot, LocalTime effectiveStartTime, String status,
            List<BookingSlotUnavailableWindowResponse> unavailableWindows) {
        return new BookingSlotResponse(
                slot.getTimeSlotId(),
                slot.getDate().atTime(effectiveStartTime).atZone(AppTimeZone.ZONE).toInstant(),
                slot.getDate().atTime(slot.getEndTime()).atZone(AppTimeZone.ZONE).toInstant(),
                status,
                unavailableWindows);
    }
}