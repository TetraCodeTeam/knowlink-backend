package com.knowlink.api.claims.controllers.responses;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;

import java.time.LocalDateTime;

/**
 * Resolution of a session claim as seen by the party that consults the detail. It never includes
 * admin data nor contact data of the counterparty.
 */
public record ClaimResolutionResponse(
        ClaimFavoredParty favoredParty,
        boolean inMyFavor,
        String justification,
        LocalDateTime resolvedAt) {}
