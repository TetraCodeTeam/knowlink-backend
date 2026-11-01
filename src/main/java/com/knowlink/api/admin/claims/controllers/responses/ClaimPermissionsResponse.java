package com.knowlink.api.admin.claims.controllers.responses;

public record ClaimPermissionsResponse(boolean canTake, boolean canViewContact, boolean canResolve) {
}