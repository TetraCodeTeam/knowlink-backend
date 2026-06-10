package com.knowlink.api.exceptions.custom_exceptions;

public class PasswordsDoNotMatchException extends RuntimeException {
    public PasswordsDoNotMatchException() {
        super("Las contraseñas no coinciden");
    }
}
