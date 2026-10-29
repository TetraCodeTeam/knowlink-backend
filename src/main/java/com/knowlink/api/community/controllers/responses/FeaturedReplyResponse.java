package com.knowlink.api.community.controllers.responses;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resumen de la respuesta destacada de una publicación resuelta, para mostrar en el feed. */
public record FeaturedReplyResponse(
        UUID replyId,
        CommunityAuthorResponse author,
        String excerpt,
        long helpfulCount,
        LocalDateTime createdAt
) {
}
