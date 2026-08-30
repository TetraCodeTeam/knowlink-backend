package com.knowlink.api.bookings.controllers.implementations;

import com.knowlink.api.bookings.controllers.interfaces.IBookingConfigController;
import com.knowlink.api.bookings.controllers.responses.BookingConfigResponse;
import com.knowlink.api.bookings.utils.BookingConstants;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingConfigControllerImpl implements IBookingConfigController {

    @Override
    public BookingConfigResponse getConfig() {
        return new BookingConfigResponse(BookingConstants.SERVICE_FEE_RATE);
    }
}