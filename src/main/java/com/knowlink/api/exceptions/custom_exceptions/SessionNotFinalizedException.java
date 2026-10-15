package com.knowlink.api.exceptions.custom_exceptions;

public class SessionNotFinalizedException extends UnprocessableEntityException {

    public SessionNotFinalizedException(String technicalMessage) {
        super("SESSION_NOT_FINALIZED", "La sesión todavía no finalizó.", technicalMessage);
    }
}