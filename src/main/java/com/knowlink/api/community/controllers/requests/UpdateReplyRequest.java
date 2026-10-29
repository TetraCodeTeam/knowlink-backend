package com.knowlink.api.community.controllers.requests;

import com.knowlink.api.community.utils.CommunityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateReplyRequest(
        @NotBlank(message = "Content is required")
        @Size(max = CommunityConstants.REPLY_MAX_CONTENT_LENGTH, message = "Content must not exceed 2000 characters")
        String content,

        /** Quita el adjunto actual. Se ignora si en la misma solicitud se envía un archivo nuevo, que lo reemplaza. */
        Boolean removeAttachment
) {
}
