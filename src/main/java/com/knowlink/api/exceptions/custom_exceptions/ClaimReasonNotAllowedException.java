package com.knowlink.api.exceptions.custom_exceptions;

public class ClaimReasonNotAllowedException extends UnprocessableEntityException {

    public ClaimReasonNotAllowedException(String technicalMessage) {
        super("CLAIM_REASON_NOT_ALLOWED_FOR_ROLE",
                "El motivo seleccionado no corresponde a tu rol en esta sesión.",
                technicalMessage);
    }
}
