package com.knowlink.api.resources.exception;

public class NoActiveReservationException extends RuntimeException {
    public NoActiveReservationException(String message) {
        super(message);
    }
}
