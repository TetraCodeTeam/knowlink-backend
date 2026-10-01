package com.knowlink.api.exceptions.custom_exceptions;

public class ClaimDeadlineExceededException extends UnprocessableEntityException {

    public ClaimDeadlineExceededException(String technicalMessage) {
        super("CLAIM_DEADLINE_EXCEEDED", "Ya pasó el plazo para reclamar esta sesión (24 horas desde que finalizó).", technicalMessage);
    }
}