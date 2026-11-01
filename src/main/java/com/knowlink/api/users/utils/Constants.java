package com.knowlink.api.users.utils;

public class Constants {
    public static final int HOURS_LIMIT = 1;
    public static final int MAX_TOKENS_PER_HOUR = 3;

    public static final int ACCOUNT_CONFIRMATION_TOKEN_HOURS = 24;
    public static final int PASSWORD_RESET_TOKEN_MINUTES = 30;

    /** Same rule as registration (StudentRegistrationRequest / TutorRegistrationRequest): lower, upper and a special character. */
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).+$";

    public static final String RESET_LINK_INVALID_CODE = "RESET_LINK_INVALID";
    public static final String RESET_LINK_INVALID_MESSAGE = "Este enlace ya no es válido. Solicitá uno nuevo";

    private Constants() {}
}