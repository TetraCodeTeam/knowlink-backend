package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record BookingSnapshot(
        UUID bookingId,
        UUID studentUserId,
        UUID tutorUserId,
        String studentName,
        String tutorName,
        LocalDate sessionDate,
        LocalTime startTime) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public String formattedDate() {
        return sessionDate.format(DATE_FORMAT);
    }

    public String formattedTime() {
        return startTime.format(TIME_FORMAT);
    }

    public String dedupeKeyFor(NotificationType type) {
        return type.name() + ":" + bookingId;
    }
}