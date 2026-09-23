package com.knowlink.api.exceptions.custom_exceptions;

import java.util.UUID;

public class ImmutableBookingException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public ImmutableBookingException(UUID bookingId) {
        super("La reserva " + bookingId + " ya tiene una transferencia procesada y no puede modificarse.");
        this.errorCode = "BOOKING_IMMUTABLE";
        this.userMessage = "La reserva ya tiene una transferencia procesada y no puede modificarse.";
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}