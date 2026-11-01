package com.knowlink.api.admin.claims.controllers.responses;

import com.knowlink.api.claims.data.enums.AutomaticOutcome;

public record AutomaticOutcomeResponse(AutomaticOutcome code, String label) {
}