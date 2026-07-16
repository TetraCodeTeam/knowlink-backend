package com.knowlink.api.exceptions.custom_exceptions;

public class ResourceNotFoundException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public ResourceNotFoundException(String errorCode, String userMessage, String technicalMessage) {
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