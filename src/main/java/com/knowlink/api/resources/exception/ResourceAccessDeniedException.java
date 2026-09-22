package com.knowlink.api.resources.exception;

public class ResourceAccessDeniedException extends RuntimeException {
    public ResourceAccessDeniedException(String message) {
        super(message);
    }
}