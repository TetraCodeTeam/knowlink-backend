package com.knowlink.api.bookings.controllers.implementations;

import com.knowlink.api.bookings.controllers.interfaces.IBookingCancellationController;
import com.knowlink.api.bookings.controllers.responses.BookingCancellationPreviewResponse;
import com.knowlink.api.bookings.controllers.responses.BookingCancellationResponse;
import com.knowlink.api.bookings.services.interfaces.IBookingCancellationService;
import com.knowlink.api.security.models.UserPrincipal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BookingCancellationControllerImpl implements IBookingCancellationController {

    private final IBookingCancellationService bookingCancellationService;

    @Override
    public BookingCancellationResponse cancelBooking(UUID bookingId, UserPrincipal principal) {
        return bookingCancellationService.cancelBooking(bookingId, principal.getUser().getUserId());
    }

    @Override
    public BookingCancellationPreviewResponse previewCancellation(UUID bookingId, UserPrincipal principal) {
        return bookingCancellationService.previewCancellation(bookingId, principal.getUser().getUserId());
    }
}
