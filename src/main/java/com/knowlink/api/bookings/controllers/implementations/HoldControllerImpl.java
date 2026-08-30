package com.knowlink.api.bookings.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.bookings.controllers.interfaces.IHoldController;
import com.knowlink.api.bookings.controllers.requests.CreateHoldRequest;
import com.knowlink.api.bookings.controllers.responses.HoldResponse;
import com.knowlink.api.bookings.services.interfaces.IHoldService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HoldControllerImpl implements IHoldController {

    private final IHoldService holdService;

    @Override
    public HoldResponse createHold(UUID tutorId, UserPrincipal principal, CreateHoldRequest request) {
        return holdService.createHold(tutorId, principal.getUser().getUserId(), request);
    }

    @Override
    public void releaseHold(UUID tutorId, UserPrincipal principal, CreateHoldRequest request) {
        holdService.releaseHold(principal.getUser().getUserId(), request);
    }
}