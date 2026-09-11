package com.knowlink.api.bookings.controllers.implementations;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.shared.responses.PagedResponse;
import com.knowlink.api.bookings.controllers.interfaces.IBookingController;
import com.knowlink.api.bookings.controllers.requests.ConfirmSessionTokenRequest;
import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingConfirmationResponse;
import com.knowlink.api.bookings.controllers.requests.VirtualSessionLinkRequest;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryDetailResponse;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryItemResponse;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;
import com.knowlink.api.bookings.services.interfaces.IBookingService;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingControllerImpl implements IBookingController {

    private final IBookingService bookingService;

    @Override
    public BookingResponse createBooking(UserPrincipal principal, CreateBookingRequest request) {
        return bookingService.createBooking(principal.getUser().getUserId(), request);
    }

    @Override
    public PagedResponse<BookingHistoryItemResponse> getHistory(UserPrincipal principal, Role role,
            BookingHistoryCategory category, int page, int size) {
        if (role != principal.getUser().getRole()) {
            throw new AccessDeniedException(
                    "No tenés permiso para consultar el historial con un rol distinto al de tu cuenta.");
        }
        return bookingService.getHistory(principal.getUser().getUserId(), role, category, page, size);
    }

    @Override
    public BookingHistoryDetailResponse getDetail(UserPrincipal principal, UUID bookingId) {
        return bookingService.getDetail(principal.getUser().getUserId(), bookingId);
    }

    @Override
    public BookingHistoryDetailResponse setVirtualLink(UserPrincipal principal, UUID bookingId,
            VirtualSessionLinkRequest request) {
        return bookingService.setVirtualLink(principal.getUser().getUserId(), bookingId, request.virtualSessionLink());
    }

    @Override
    public BookingConfirmationResponse confirmSession(UserPrincipal principal, UUID bookingId,
            ConfirmSessionTokenRequest request) {
        return bookingService.confirmSession(principal.getUser().getUserId(), bookingId, request.token());
    }
}