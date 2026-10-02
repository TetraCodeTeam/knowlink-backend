package com.knowlink.api.ratings.controllers.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.knowlink.api.ratings.utils.RatingConstants;

public record CreateRatingRequest(
        @NotNull(message = "Score is required")
        @Min(value = 1, message = "Score must be at least 1")
        @Max(value = 5, message = "Score must be at most 5")
        Integer score,
                @Size(max = RatingConstants.MAX_COMMENT_LENGTH, message = "Comment must not exceed 2000 characters")
                String comment) {

        public CreateRatingRequest {
                if (comment != null) {
                        comment = comment.trim();
                        if (comment.isEmpty()) {
                                comment = null;
                        }
                }
        }
}