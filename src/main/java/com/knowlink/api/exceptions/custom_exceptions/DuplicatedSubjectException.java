package com.knowlink.api.exceptions.custom_exceptions;

import java.util.UUID;

public class DuplicatedSubjectException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;
    private final UUID existingSubjectId;

    public DuplicatedSubjectException(String errorCode, String userMessage, String technicalMessage,
            UUID existingSubjectId) {
        super(technicalMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.existingSubjectId = existingSubjectId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public UUID getExistingSubjectId() {
        return existingSubjectId;
    }
}