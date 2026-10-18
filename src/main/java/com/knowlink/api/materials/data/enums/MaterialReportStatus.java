package com.knowlink.api.materials.data.enums;

public enum MaterialReportStatus {
    PENDING,
    IN_REVIEW,
    RESOLVED;

    public boolean canTransitionTo(MaterialReportStatus nextStatus) {
        if (this == nextStatus) {
            return true;
        }
        return (this == PENDING && (nextStatus == IN_REVIEW || nextStatus == RESOLVED))
                || (this == IN_REVIEW && nextStatus == RESOLVED);
    }
}
