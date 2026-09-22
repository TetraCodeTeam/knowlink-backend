package com.knowlink.api.bookings.services.interfaces;

import com.knowlink.api.bookings.controllers.requests.CreateHoldRequest;
import com.knowlink.api.bookings.controllers.responses.HoldResponse;

import java.util.UUID;

public interface IHoldService {
    HoldResponse createHold(UUID tutorUserId, UUID studentUserId, CreateHoldRequest request);
    void releaseHold(UUID studentUserId, CreateHoldRequest request);
    void expireOverdueHolds();
}