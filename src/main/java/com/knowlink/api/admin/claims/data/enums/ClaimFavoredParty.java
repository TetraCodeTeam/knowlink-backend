package com.knowlink.api.admin.claims.data.enums;

/**
 * Party favored by the administrator resolution of a session claim dispute.
 */
public enum ClaimFavoredParty {

    TUTOR("Tutor"),
    STUDENT("Alumno");

    private final String displayLabel;

    ClaimFavoredParty(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }
}