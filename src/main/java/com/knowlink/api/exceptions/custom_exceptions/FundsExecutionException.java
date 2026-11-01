package com.knowlink.api.exceptions.custom_exceptions;

/**
 * Failure while executing the funds movement of a claim resolution.
 * The whole operation is rolled back.
 */
public class FundsExecutionException extends RuntimeException {

    private static final String ERROR_CODE = "FUNDS_EXECUTION_FAILED";
    private static final String USER_MESSAGE =
            "No pudimos ejecutar la transferencia de fondos. El reclamo sigue en revisión; reintentá.";

    public FundsExecutionException(String technicalMessage) {
        super(technicalMessage);
    }

    public FundsExecutionException(String technicalMessage, Throwable cause) {
        super(technicalMessage, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }

    public String getUserMessage() {
        return USER_MESSAGE;
    }
}