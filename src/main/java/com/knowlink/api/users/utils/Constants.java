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

    public static final String ACCOUNT_HAS_ACTIVE_BOOKINGS_CODE = "ACCOUNT_HAS_ACTIVE_BOOKINGS";
    public static final String ACCOUNT_HAS_ACTIVE_BOOKINGS_MESSAGE =
            "No podés dar de baja tu cuenta con reservas activas pendientes";

    public static final String ACCOUNT_HAS_ACTIVE_CLAIMS_CODE = "ACCOUNT_HAS_ACTIVE_CLAIMS";
    public static final String ACCOUNT_HAS_ACTIVE_CLAIMS_MESSAGE =
            "No pod\u00e9s dar de baja tu cuenta con reclamos abiertos";

    public static final String ACCOUNT_HAS_RETAINED_FUNDS_CODE = "ACCOUNT_HAS_RETAINED_FUNDS";
    public static final String ACCOUNT_HAS_RETAINED_FUNDS_MESSAGE =
            "No pod\u00e9s dar de baja tu cuenta con fondos retenidos pendientes";

    public static final String ACCOUNT_HAS_PENDING_OBLIGATIONS_CODE = "ACCOUNT_HAS_PENDING_OBLIGATIONS";
    public static final String ACCOUNT_HAS_PENDING_OBLIGATIONS_MESSAGE =
            "No pod\u00e9s dar de baja tu cuenta con pagos o reclamos pendientes de resoluci\u00f3n.";

    /** Display name used for an anonymized (permanently deleted) account (US-24). */
    public static final String DELETED_USER_DISPLAY_NAME = "Usuario eliminado";

    /** Tombstone email: unique, NOT NULL and under the reserved .invalid domain (RFC 2606). */
    public static final String DELETED_EMAIL_PREFIX = "deleted+";
    public static final String DELETED_EMAIL_DOMAIN = "@knowlink.invalid";

    private Constants() {}
}