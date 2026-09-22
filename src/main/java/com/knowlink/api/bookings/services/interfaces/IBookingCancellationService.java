package com.knowlink.api.bookings.services.interfaces;

import com.knowlink.api.bookings.controllers.responses.BookingCancellationPreviewResponse;
import com.knowlink.api.bookings.controllers.responses.BookingCancellationResponse;

import java.util.UUID;

public interface IBookingCancellationService {

    BookingCancellationResponse cancelBooking(UUID bookingId, UUID currentUserId);

    BookingCancellationPreviewResponse previewCancellation(UUID bookingId, UUID currentUserId);
}
