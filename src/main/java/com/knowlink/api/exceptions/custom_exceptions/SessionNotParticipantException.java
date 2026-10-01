package com.knowlink.api.exceptions.custom_exceptions;

public class SessionNotParticipantException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public SessionNotParticipantException(String technicalMessage) {
        super(technicalMessage);
        this.errorCode = "SESSION_NOT_PARTICIPANT";
        this.userMessage = "No participás de esta sesión.";
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}