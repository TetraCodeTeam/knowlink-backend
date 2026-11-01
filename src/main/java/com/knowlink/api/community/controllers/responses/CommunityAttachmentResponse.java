package com.knowlink.api.community.controllers.responses;

/** Adjunto de una publicación o respuesta. La URL es firmada y temporal; puede ser null si el storage no responde. */
public record CommunityAttachmentResponse(
        String fileName,
        String contentType,
        long sizeInBytes,
        String url
) {
}
