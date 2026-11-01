package com.knowlink.api.community.controllers.responses;

import java.util.List;

/** Hilo completo: la publicación y todas sus respuestas (la destacada primero, luego por antigüedad). */
public record CommunityPostDetailResponse(
        CommunityPostResponse post,
        List<CommunityReplyResponse> replies
) {
}
