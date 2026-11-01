package com.knowlink.api.exceptions.custom_exceptions;

/**
 * The current administrator is not the one assigned to the case.
 */
public class NotAssignedToAdminException extends RuntimeException {

    private static final String ERROR_CODE = "NOT_ASSIGNED_TO_ADMIN";
    private static final String USER_MESSAGE = "Solo el administrador asignado puede resolver esta solicitud.";

    public NotAssignedToAdminException(String technicalMessage) {
        super(technicalMessage);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }

    public String getUserMessage() {
        return USER_MESSAGE;
    }
}