package com.knowlink.api.exceptions.custom_exceptions;

public class ConfirmationBlockedByClaimException extends UnprocessableEntityException {

    public ConfirmationBlockedByClaimException(String technicalMessage) {
        super("CONFIRMATION_BLOCKED_BY_ACTIVE_CLAIM",
                "No se puede confirmar la clase porque hay un reclamo activo sobre esta sesión.",
                technicalMessage);
    }
}
