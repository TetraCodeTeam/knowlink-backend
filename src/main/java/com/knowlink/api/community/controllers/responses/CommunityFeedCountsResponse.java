package com.knowlink.api.community.controllers.responses;

/** Cantidad de publicaciones por tipo de consulta, con todos los demás filtros aplicados. */
public record CommunityFeedCountsResponse(
        long total,
        long helpRequests,
        long sharedContents
) {
}
