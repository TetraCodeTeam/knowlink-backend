package com.knowlink.api.notifications.data.enums;

public enum NotificationType {

    // Connected in this story (the domain event already exists or is created here)
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    SESSION_CONFIRMATION_REMINDER,
    SESSION_NOT_CONFIRMED,
    SESSION_CONFIRMED,
    SESSION_COMPLETED,
    RATING_ENABLED,
    FUNDS_TRANSFERRED,
    FUNDS_SUSPENDED,
    FUNDS_RELEASED,

    // Defined here; each owning story connects them through INotificationService
    SUBJECT_VALIDATION_UPDATED,
    MATERIAL_REPORT_PROCESSED,
    ADMIN_VALIDATION_REQUEST_PENDING,
    ADMIN_MATERIAL_REPORT_RECEIVED,

    // Support notifications already in use (claims, materials, tutor support)
    SESSION_CLAIM_UPDATE,
    SESSION_CLAIM_COMMENT,
    MATERIAL_REPORT_UPDATE
}