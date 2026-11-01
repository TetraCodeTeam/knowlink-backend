package com.knowlink.api.admin.claims.controllers.responses;

public record ClaimPartyStatementResponse(
        String fullName,
        String reasonCode,
        String reasonLabel,
        String message) {
}