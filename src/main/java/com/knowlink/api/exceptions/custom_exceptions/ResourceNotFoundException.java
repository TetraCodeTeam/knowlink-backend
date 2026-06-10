package com.knowlink.api.exceptions.custom_exceptions;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s con %s '%s' no encontrado", resource, field, value));
    }
}
