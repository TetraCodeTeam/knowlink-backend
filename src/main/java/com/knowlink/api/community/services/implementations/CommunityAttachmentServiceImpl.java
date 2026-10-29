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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class CommunityAttachmentServiceImpl implements ICommunityAttachmentService {

    private static final int MAX_SAFE_BASE_LENGTH = 100;
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
        // Supabase rechaza claves con espacios o tildes (InvalidKey): se sube con un nombre seguro y la
        // metadata conserva el nombre original para mostrarlo.
        String storagePath = storageService.upload(
                new StorageSafeMultipartFile(file, toSafeFileName(file.getOriginalFilename())),
                CommunityConstants.STORAGE_FOLDER + "/" + ownerId);
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
            // Un storage caído no debe tirar abajo el feed: el adjunto se muestra sin enlace.
            log.warn("Failed to sign community attachment {}: {}", attachment.getStoragePath(), ex.getMessage());
        }
        return new CommunityAttachmentResponse(
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSizeInBytes() == null ? 0L : attachment.getSizeInBytes(),
                url);
    }

    /** "Logotipo UTN Villa María en azul.png" -> "Logotipo_UTN_Villa_Maria_en_azul.png". */
    static String toSafeFileName(String originalFileName) {
        String name = originalFileName == null ? "" : originalFileName;
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot + 1);

        String safeBase = sanitize(base);
        if (safeBase.length() > MAX_SAFE_BASE_LENGTH) {
            safeBase = safeBase.substring(0, MAX_SAFE_BASE_LENGTH);
        }
        if (safeBase.isEmpty()) {
            safeBase = "archivo";
        }
        String safeExtension = sanitize(extension).toLowerCase(Locale.ROOT);
        return safeExtension.isEmpty() ? safeBase : safeBase + "." + safeExtension;
    }

    private static String sanitize(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Vista del archivo original con otro nombre: solo cambia getOriginalFilename(), que arma la clave en storage. */
    private static final class StorageSafeMultipartFile implements MultipartFile {

        private final MultipartFile delegate;
        private final String fileName;

        private StorageSafeMultipartFile(MultipartFile delegate, String fileName) {
            this.delegate = delegate;
            this.fileName = fileName;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getOriginalFilename() {
            return fileName;
        }

        @Override
        public String getContentType() {
            return delegate.getContentType();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public long getSize() {
            return delegate.getSize();
        }

        @Override
        public byte[] getBytes() throws IOException {
            return delegate.getBytes();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            delegate.transferTo(dest);
        }
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
