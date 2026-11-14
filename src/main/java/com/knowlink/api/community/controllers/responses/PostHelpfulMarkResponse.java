package com.knowlink.api.community.controllers.responses;

import java.util.UUID;

/** Resultado de marcar o quitar "Esto me sirvió" en un aporte: el total vigente y si el usuario lo tiene marcado. */
public record PostHelpfulMarkResponse(
        UUID postId,
        long helpfulCount,
        boolean markedByMe
) {
}
