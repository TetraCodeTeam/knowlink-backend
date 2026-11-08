package com.knowlink.api.ratings.controllers.implementations;

import com.knowlink.api.ratings.controllers.interfaces.IRatingGateController;
import com.knowlink.api.ratings.controllers.responses.PendingRatingsResponse;
import com.knowlink.api.ratings.services.interfaces.IRatingGateService;
import com.knowlink.api.security.models.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RatingGateControllerImpl implements IRatingGateController {

    private final IRatingGateService ratingGateService;

    @Override
    public PendingRatingsResponse getPending(UserPrincipal principal) {
        return ratingGateService.getPending(principal.getUser().getUserId());
    }
}
