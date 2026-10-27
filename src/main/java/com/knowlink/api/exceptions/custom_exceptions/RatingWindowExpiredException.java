package com.knowlink.api.exceptions.custom_exceptions;

public class RatingWindowExpiredException extends UnprocessableEntityException {

    public RatingWindowExpiredException(String technicalMessage) {
        super("RATING_WINDOW_EXPIRED",
                "Ya pasó el plazo para calificar esta sesión (24 horas desde que finalizó).",
                technicalMessage);
    }
}