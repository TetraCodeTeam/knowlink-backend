package com.knowlink.api.exceptions.custom_exceptions;

/**
 * Business conflict that is not a duplicate resource (e.g. a case already taken).
 */
public class BusinessConflictException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public BusinessConflictException(String errorCode, String userMessage, String technicalMessage) {
        super(technicalMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}