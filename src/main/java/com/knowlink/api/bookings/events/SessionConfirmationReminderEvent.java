package com.knowlink.api.bookings.events;

import com.knowlink.api.bookings.data.models.Booking;

import java.time.LocalDateTime;

public record SessionConfirmationReminderEvent(Booking booking, LocalDateTime reminderAt) {
}
