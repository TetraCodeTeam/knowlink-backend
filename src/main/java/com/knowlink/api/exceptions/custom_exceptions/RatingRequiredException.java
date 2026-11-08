package com.knowlink.api.exceptions.custom_exceptions;

public class RatingRequiredException extends RuntimeException {

    private static final String ERROR_CODE = "RATING_REQUIRED";
    private static final String USER_MESSAGE = "Tenés que calificar tu sesión para poder seguir usando KnowLink.";

    public RatingRequiredException() {
        super("User has a pending mandatory rating");
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }

    public String getUserMessage() {
        return USER_MESSAGE;
    }
}
