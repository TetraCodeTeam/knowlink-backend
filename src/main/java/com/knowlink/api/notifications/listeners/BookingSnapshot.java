package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.utils.NotificationTextFormats;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingSnapshot(
        UUID bookingId,
        UUID studentUserId,
        UUID tutorUserId,
        String studentName,
        String tutorName,
        String subjectName,
        LocalDate sessionDate,
        LocalTime startTime) {

    public String formattedDate() {
        return NotificationTextFormats.date(sessionDate);
    }

    public String formattedDateWithWeekday() {
        return NotificationTextFormats.dateWithWeekday(sessionDate);
    }

    public String formattedTime() {
        return NotificationTextFormats.time(startTime);
    }

    public String dedupeKeyFor(NotificationType type) {
        return type.name() + ":" + bookingId;
    }
}