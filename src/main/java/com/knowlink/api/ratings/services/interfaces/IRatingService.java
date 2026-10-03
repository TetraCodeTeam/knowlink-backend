package com.knowlink.api.ratings.services.interfaces;

import com.knowlink.api.ratings.controllers.requests.CreateRatingRequest;
import com.knowlink.api.ratings.controllers.responses.RatingResponse;

import java.util.UUID;

public interface IRatingService {
    RatingResponse submitRating(UUID userId, UUID bookingId, CreateRatingRequest request);
}