package com.knowlink.api.bookings.events;

import java.util.UUID;

public record BookingCreatedEvent(UUID bookingId) {}