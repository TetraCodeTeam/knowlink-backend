package com.knowlink.api.admin.claims.controllers.responses;

public record PartyContactResponse(
        String fullName,
        String email,
        String phoneNumber,
        String dni,
        String institutionalId,
        String career) {
}