package com.knowlink.api.tutors.controllers.responses;

import java.util.List;

public record PagedCommentsResponse(
        List<RatingCommentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}