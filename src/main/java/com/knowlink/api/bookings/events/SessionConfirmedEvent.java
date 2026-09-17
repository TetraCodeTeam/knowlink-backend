package com.knowlink.api.bookings.events;

import com.knowlink.api.bookings.data.models.Booking;

public record SessionConfirmedEvent(Booking booking) {}

