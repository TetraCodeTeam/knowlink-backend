package com.knowlink.api.tutors.support.validations;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.materials.exception.FormatNotAllowedException;
import com.knowlink.api.support.config.SupportProperties;
import com.knowlink.api.support.utils.FileFormatConstants;
import com.knowlink.api.tutors.support.controllers.requests.SubmitMaterialDefenseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MaterialDefenseValidator {

    private final SupportProperties supportProperties;

    public List<MultipartFile> normalizeAndValidate(
            SubmitMaterialDefenseRequest request,
            List<MultipartFile> files) {
        List<MultipartFile> attachments = files == null
                ? List.of()
                : files.stream().filter(Objects::nonNull).toList();
        if (request == null) {
            throw new ValidationException("El descargo es obligatorio.");
        }
        String description = request.description();
        if ((description == null || description.isBlank()) && attachments.isEmpty()) {
            throw new ValidationException("Incluí una explicación o al menos una prueba de autoría.");
        }

        int maxAttachments = supportProperties.materialDefense().maxAttachments();
        long maxAttachmentBytes = supportProperties.materialDefense().maxAttachmentBytes();
        if (attachments.size() > maxAttachments) {
            throw new ValidationException("No podés adjuntar más de " + maxAttachments + " archivos.");
        }

        for (MultipartFile file : attachments) {
            validateAttachment(file, maxAttachmentBytes);
        }
        return attachments;
    }

    private void validateAttachment(MultipartFile file, long maxAttachmentBytes) {
        if (file.isEmpty()) {
            throw new ValidationException("El archivo adjunto no puede estar vacío.");
        }
        if (file.getSize() > maxAttachmentBytes) {
            throw new ValidationException("Cada archivo adjunto no puede superar los "
                    + (maxAttachmentBytes / (1024 * 1024)) + "MB.");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.length() > 255) {
            throw new FormatNotAllowedException("El formato del archivo no está permitido");
        }
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!FileFormatConstants.ALLOWED_EXTENSIONS.contains(extension)
                || !Objects.equals(
                        FileFormatConstants.ALLOWED_CONTENT_TYPES.get(extension), file.getContentType())) {
            throw new FormatNotAllowedException("El formato del archivo no está permitido");
        }
    }
}
