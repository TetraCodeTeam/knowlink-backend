package com.knowlink.api.exceptions.custom_exceptions;

public class EmailAlreadyVerifiedException extends RuntimeException {

    public EmailAlreadyVerifiedException(String message) {
        super(message);
    }

}
