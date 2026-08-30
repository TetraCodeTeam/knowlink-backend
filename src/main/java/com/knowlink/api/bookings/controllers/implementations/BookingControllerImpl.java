package com.knowlink.api.bookings.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.bookings.controllers.interfaces.IBookingController;
import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.services.interfaces.IBookingService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingControllerImpl implements IBookingController {

    private final IBookingService bookingService;

    @Override
    public BookingResponse createBooking(UserPrincipal principal, CreateBookingRequest request) {
        return bookingService.createBooking(principal.getUser().getUserId(), request);
    }
}