package com.knowlink.api.admin.materialreports.controllers.responses;

import java.util.UUID;

public record AssignedAdminResponse(UUID adminId, String fullName) {
}