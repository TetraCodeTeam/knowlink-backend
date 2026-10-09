package com.knowlink.api.materials.exception;

public class NoActiveReservationException extends RuntimeException {
    public NoActiveReservationException(String message) {
        super(message);
    }
}
