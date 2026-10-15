package com.knowlink.api.exceptions.custom_exceptions;

public class SessionAlreadyConfirmedException extends UnprocessableEntityException {

    public SessionAlreadyConfirmedException(String technicalMessage) {
        super("SESSION_ALREADY_CONFIRMED",
                "La sesión ya fue confirmada con el código, por lo que no se puede reclamar por asistencia.",
                technicalMessage);
    }
}
