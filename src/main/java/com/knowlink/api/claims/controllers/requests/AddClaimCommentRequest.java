package com.knowlink.api.claims.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddClaimCommentRequest(
        @NotBlank(message = "Comment is required")
        @Size(max = 2000, message = "Comment must not exceed {max} characters")
        String comment) {

    public AddClaimCommentRequest {
        if (comment != null) {
            comment = comment.trim();
        }
    }
}
