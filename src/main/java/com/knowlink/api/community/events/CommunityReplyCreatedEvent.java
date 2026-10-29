package com.knowlink.api.community.events;

import com.knowlink.api.security.enums.Role;

import java.util.UUID;

/**
 * Se publica al guardar una respuesta nueva. El listener de notificaciones avisa al autor de la publicación
 * (salvo que se responda a sí mismo).
 */
public record CommunityReplyCreatedEvent(
        UUID replyId,
        UUID postId,
        String postTitle,
        UUID postAuthorId,
        Role postAuthorRole,
        UUID replierId,
        String replierName
) {
}
