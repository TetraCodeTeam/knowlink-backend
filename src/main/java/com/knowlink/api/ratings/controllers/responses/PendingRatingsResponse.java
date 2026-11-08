package com.knowlink.api.ratings.controllers.responses;

import java.time.LocalDateTime;
import java.util.List;

public record PendingRatingsResponse(
        List<PendingRatingItemResponse> blocking,
        LocalDateTime nextBlockAt) {
}
