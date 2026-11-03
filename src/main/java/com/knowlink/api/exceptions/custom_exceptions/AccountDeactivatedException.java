package com.knowlink.api.exceptions.custom_exceptions;

/**
 * The credentials are correct but the account was temporarily deactivated by the user (US-04).
 */
public class AccountDeactivatedException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public AccountDeactivatedException() {
        this("The account is temporarily deactivated");
    }

    public AccountDeactivatedException(String technicalMessage) {
        super(technicalMessage);
        this.errorCode = "ACCOUNT_DEACTIVATED";
        this.userMessage = "Tu cuenta está desactivada. Podés reactivarla para volver a usar KnowLink.";
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}