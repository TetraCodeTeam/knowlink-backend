package com.knowlink.api.admin.claims.controllers.requests;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveSessionClaimRequest(
        @NotNull(message = "Favored party is required") ClaimFavoredParty favoredParty,

        @NotBlank(message = "Justification is required")
        @Size(max = 1000, message = "Justification must be at most 1000 characters") String justification) {
}