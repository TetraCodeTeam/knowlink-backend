package com.knowlink.api.community.controllers.responses;

import java.util.UUID;

public record HelpfulMarkResponse(
        UUID replyId,
        long helpfulCount,
        boolean markedByMe
) {
}
