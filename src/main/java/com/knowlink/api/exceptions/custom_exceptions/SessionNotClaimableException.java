package com.knowlink.api.exceptions.custom_exceptions;

public class SessionNotClaimableException extends UnprocessableEntityException {

    public SessionNotClaimableException(String technicalMessage) {
        super("SESSION_NOT_CLAIMABLE", "Esta sesión no admite reclamos.", technicalMessage);
    }
}
