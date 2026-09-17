package com.knowlink.api.bookings.events;

public record SessionConfirmationTokenGeneratedEvent(BookingConfirmationEventPayload booking, String rawToken) {}
