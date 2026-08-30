package com.knowlink.api.bookings.services.interfaces;

import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;

import java.util.UUID;

public interface IBookingService {
    BookingResponse createBooking(UUID studentUserId, CreateBookingRequest request);
}
