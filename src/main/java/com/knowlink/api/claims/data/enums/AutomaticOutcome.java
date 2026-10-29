package com.knowlink.api.claims.data.enums;

/**
 * Outcome the system would have applied automatically, persisted when the claim is created.
 */
public enum AutomaticOutcome {

    SESSION_COMPLETED_RELEASE_TO_TUTOR("Sesión realizada — liberación al tutor"),
    TUTOR_ABSENT_REFUND_TO_STUDENT("Tutor ausente — reintegro al alumno"),
    SESSION_NOT_HELD_REFUND_TO_STUDENT("Sesión no realizada — reintegro al alumno");

    private final String label;

    AutomaticOutcome(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}