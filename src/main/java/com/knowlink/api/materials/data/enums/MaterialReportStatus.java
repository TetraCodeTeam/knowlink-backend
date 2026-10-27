package com.knowlink.api.materials.data.enums;

import java.util.Collection;

public enum MaterialReportStatus {
    PENDING("Pendiente"),
    IN_REVIEW("En revisión"),
    RESOLVED("Resuelto");

    private final String displayLabel;

    MaterialReportStatus(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public static MaterialReportStatus orPending(MaterialReportStatus status) {
        return status == null ? PENDING : status;
    }

    public static MaterialReportStatus aggregate(Collection<MaterialReportStatus> statuses) {
        if (statuses.stream().anyMatch(status -> orPending(status) == IN_REVIEW)) {
            return IN_REVIEW;
        }
        if (statuses.stream().anyMatch(status -> orPending(status) == PENDING)) {
            return PENDING;
        }
        return RESOLVED;
    }

    public boolean canTransitionTo(MaterialReportStatus nextStatus) {
        if (this == nextStatus) {
            return true;
        }
        return (this == PENDING && (nextStatus == IN_REVIEW || nextStatus == RESOLVED))
                || (this == IN_REVIEW && nextStatus == RESOLVED);
    }
}
