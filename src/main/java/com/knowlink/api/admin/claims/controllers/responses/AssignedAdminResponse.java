package com.knowlink.api.admin.claims.controllers.responses;

import java.util.UUID;

public record AssignedAdminResponse(UUID adminId, String fullName) {
}