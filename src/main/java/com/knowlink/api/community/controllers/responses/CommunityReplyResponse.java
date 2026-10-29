package com.knowlink.api.community.controllers.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommunityReplyResponse(
        UUID replyId,
        UUID postId,
        CommunityAuthorResponse author,
        String content,
        CommunityAttachmentResponse attachment,
        long helpfulCount,
        boolean markedHelpfulByMe,
        boolean featured,
        BookingCardResponse bookingCard,
        LocalDateTime createdAt,
        boolean edited,
        boolean ownedByMe
) {
}
