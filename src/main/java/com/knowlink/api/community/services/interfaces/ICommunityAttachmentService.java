package com.knowlink.api.community.services.interfaces;

import com.knowlink.api.community.controllers.responses.CommunityAttachmentResponse;
import com.knowlink.api.community.data.models.CommunityAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ICommunityAttachmentService {

    /**
     * Valida el archivo (no vacío, tamaño y formato permitidos). Un archivo null se considera "sin adjunto".
     */
    void validate(MultipartFile file);

    /**
     * Sube el archivo al storage y devuelve su metadata. Devuelve null si no hay archivo.
     */
    CommunityAttachment store(MultipartFile file, UUID ownerId);

    /**
     * Borra el archivo del storage sin propagar errores: un adjunto huérfano no debe romper la operación.
     */
    void deleteQuietly(CommunityAttachment attachment);

    CommunityAttachmentResponse toResponse(CommunityAttachment attachment);
}
