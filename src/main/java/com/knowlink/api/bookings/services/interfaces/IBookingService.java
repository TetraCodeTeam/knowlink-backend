package com.knowlink.api.bookings.services.interfaces;

import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryDetailResponse;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryItemResponse;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.responses.PagedResponse;

import java.util.UUID;

public interface IBookingService {
    BookingResponse createBooking(UUID studentUserId, CreateBookingRequest request);

    PagedResponse<BookingHistoryItemResponse> getHistory(UUID userId, Role role, BookingHistoryCategory category,
            int page, int size);

    BookingHistoryDetailResponse getDetail(UUID userId, UUID bookingId);

    BookingHistoryDetailResponse setVirtualLink(UUID tutorUserId, UUID bookingId, String virtualSessionLink);
}
