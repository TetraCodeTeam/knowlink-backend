package com.knowlink.api.admin.materialreports.data.enums;

public enum MaterialReportCaseStatus {

    PENDING("Pendiente"),
    IN_REVIEW("En revisión"),
    REMOVED("Retirado"),
    DISMISSED("No procedente");

    private final String displayLabel;

    MaterialReportCaseStatus(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public boolean isOpen() {
        return this == PENDING || this == IN_REVIEW;
    }

    public boolean isClosed() {
        return this == REMOVED || this == DISMISSED;
    }
}