package com.knowlink.api.ratings.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.ratings.controllers.interfaces.IRatingsController;
import com.knowlink.api.ratings.controllers.requests.CreateRatingRequest;
import com.knowlink.api.ratings.controllers.responses.RatingResponse;
import com.knowlink.api.ratings.services.interfaces.IRatingService;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RatingsControllerImpl implements IRatingsController {

    private final IRatingService bookingRatingService;

    @Override
    public RatingResponse submitRating(UserPrincipal principal, UUID bookingId,
            CreateRatingRequest request) {
        return bookingRatingService.submitRating(principal.getUser().getUserId(), bookingId, request);
    }

}
