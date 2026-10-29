package com.knowlink.api.community.controllers.responses;

import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publicación del feed. helpfulCount es la cantidad de marcas "Esto me sirvió" de su mejor respuesta
 * (la destacada si la publicación está resuelta); featuredReply solo viene en publicaciones resueltas.
 * canReply indica si el usuario que consulta puede responder: la publicación debe estar abierta y, si es del propio
 * usuario, tiene que haber respondido alguien más.
 */
public record CommunityPostResponse(
        UUID postId,
        PostType type,
        PostStatus status,
        CommunitySubjectResponse subject,
        String title,
        String content,
        CommunityAttachmentResponse attachment,
        CommunityAuthorResponse author,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        int replyCount,
        long helpfulCount,
        FeaturedReplyResponse featuredReply,
        boolean ownedByMe,
        boolean edited,
        boolean canReply
) {
}
