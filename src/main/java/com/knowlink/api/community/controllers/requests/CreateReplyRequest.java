package com.knowlink.api.community.controllers.requests;

import com.knowlink.api.community.utils.CommunityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReplyRequest(
        @NotBlank(message = "Content is required")
        @Size(max = CommunityConstants.REPLY_MAX_CONTENT_LENGTH, message = "Content must not exceed 2000 characters")
        String content,

        /** Solo aplica a respuestas de un tutor; si falta, la tarjeta se incluye. */
        Boolean includeBookingCard,

        @Size(max = CommunityConstants.BOOKING_QUESTION_MAX_LENGTH,
                message = "Booking question must not exceed 150 characters")
        String bookingCardQuestion
) {
}
