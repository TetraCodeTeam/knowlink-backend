package com.knowlink.api.community.controllers.requests;

import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.utils.CommunityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePostRequest(
        @NotNull(message = "Post type is required")
        PostType type,

        @NotNull(message = "Subject is required")
        UUID subjectId,

        @NotBlank(message = "Title is required")
        @Size(max = CommunityConstants.MAX_TITLE_LENGTH, message = "Title must not exceed 150 characters")
        String title,

        @NotBlank(message = "Content is required")
        @Size(max = CommunityConstants.SHARED_CONTENT_MAX_CONTENT_LENGTH,
                message = "Content must not exceed 2000 characters")
        String content
) {
}
