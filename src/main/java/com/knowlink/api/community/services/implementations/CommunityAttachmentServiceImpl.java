package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.controllers.responses.CommunityAttachmentResponse;
import com.knowlink.api.community.data.models.CommunityAttachment;
import com.knowlink.api.community.services.interfaces.ICommunityAttachmentService;
import com.knowlink.api.community.utils.CommunityConstants;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.materials.exception.FormatNotAllowedException;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.support.utils.FileFormatConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class CommunityAttachmentServiceImpl implements ICommunityAttachmentService {

    private static final String FORMAT_NOT_ALLOWED_MESSAGE = "El formato del archivo no está permitido";

    private final ISupabaseStorageService storageService;
    private final long maxAttachmentBytes;

    public CommunityAttachmentServiceImpl(
            ISupabaseStorageService storageService,
            @Value("${knowlink.community.max-attachment-mb:5}") long maxAttachmentMb) {
        this.storageService = storageService;
        this.maxAttachmentBytes = maxAttachmentMb * 1024 * 1024;
    }

    @Override
    public void validate(MultipartFile file) {
        if (file == null) {
            return;
        }
        if (file.isEmpty()) {
            throw new ValidationException("El archivo adjunto no puede estar vacío.");
        }
        if (file.getSize() > maxAttachmentBytes) {
            throw new ValidationException(
                    "El archivo adjunto no puede superar los " + (maxAttachmentBytes / (1024 * 1024)) + "MB.");
        }
        validateFormat(file);
    }

    @Override
    public CommunityAttachment store(MultipartFile file, UUID ownerId) {
        if (file == null) {
            return null;
        }
        validate(file);
        // The storage service sanitizes the object key (Supabase rejects spaces, accents and
        // other symbols); the original file name is kept here so it can still be displayed.
        String storagePath = storageService.upload(file, CommunityConstants.STORAGE_FOLDER + "/" + ownerId);
        return CommunityAttachment.builder()
                .storagePath(storagePath)
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeInBytes(file.getSize())
                .build();
    }

    @Override
    public void deleteQuietly(CommunityAttachment attachment) {
        if (attachment == null || attachment.getStoragePath() == null) {
            return;
        }
        try {
            storageService.delete(attachment.getStoragePath());
        } catch (RuntimeException ex) {
            log.warn("Failed to delete community attachment {}: {}", attachment.getStoragePath(), ex.getMessage());
        }
    }

    @Override
    public CommunityAttachmentResponse toResponse(CommunityAttachment attachment) {
        if (attachment == null || attachment.getStoragePath() == null) {
            return null;
        }
        String url = null;
        try {
            url = storageService.generateSignedUrl(
                    attachment.getStoragePath(), CommunityConstants.SIGNED_URL_EXPIRATION_SECONDS);
        } catch (RuntimeException ex) {
            // A storage outage must not take the feed down: the attachment is shown without a link.
            log.warn("Failed to sign community attachment {}: {}", attachment.getStoragePath(), ex.getMessage());
        }
        return new CommunityAttachmentResponse(
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSizeInBytes() == null ? 0L : attachment.getSizeInBytes(),
                url);
    }

    private void validateFormat(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new FormatNotAllowedException(FORMAT_NOT_ALLOWED_MESSAGE);
        }
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot < 0 ? "" : originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!FileFormatConstants.ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FormatNotAllowedException(FORMAT_NOT_ALLOWED_MESSAGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !FileFormatConstants.ALLOWED_CONTENT_TYPES.get(extension).equals(contentType)) {
            throw new FormatNotAllowedException(FORMAT_NOT_ALLOWED_MESSAGE);
        }
    }
}