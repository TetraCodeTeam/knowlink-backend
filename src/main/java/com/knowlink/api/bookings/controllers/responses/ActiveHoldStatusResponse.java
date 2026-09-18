package com.knowlink.api.bookings.controllers.responses;

public record ActiveHoldStatusResponse(boolean hasActiveHold, ActiveHoldResponse hold) {}