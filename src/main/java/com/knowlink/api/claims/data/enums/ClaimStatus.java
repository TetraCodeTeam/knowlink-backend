package com.knowlink.api.claims.data.enums;

import java.util.Set;

public enum ClaimStatus {
    OPEN("Pendiente"),
    IN_REVIEW("En revisión"),
    RESOLVED("Resuelto");

    private final String displayLabel;

    ClaimStatus(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public static final Set<ClaimStatus> ACTIVE_STATUSES = Set.of(OPEN, IN_REVIEW);

    public boolean canTransitionTo(ClaimStatus nextStatus) {
        if (this == nextStatus) {
            return true;
        }
        return (this == OPEN && (nextStatus == IN_REVIEW || nextStatus == RESOLVED))
                || (this == IN_REVIEW && nextStatus == RESOLVED);
    }
}
