package com.knowlink.api.exceptions.custom_exceptions;

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String resource, String field, Object value) {
        super(String.format("%s con %s '%s' ya existe", resource, field, value));
    }
}
