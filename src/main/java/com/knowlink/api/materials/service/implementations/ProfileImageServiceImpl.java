package com.knowlink.api.materials.service.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.materials.exception.FormatNotAllowedException;
import com.knowlink.api.materials.exception.StorageServiceException;
import com.knowlink.api.materials.service.interfaces.IProfileImageService;
import com.knowlink.api.support.utils.FileNameSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ProfileImageServiceImpl implements IProfileImageService {

    private static final String STORAGE_ERROR_MESSAGE =
            "No se pudo comunicar con el almacenamiento de fotos. Intentá nuevamente más tarde.";

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${supabase.storage.profile-photos-bucket:profilePhotos}")
    private String bucket;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    private final RestClient restClient;

    public ProfileImageServiceImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String upload(MultipartFile file, UUID userId) {
        validateFile(file);

        // Supabase rejects object keys containing spaces, accents or other symbols (InvalidKey).
        String path = userId + "/" + UUID.randomUUID() + "-"
                + FileNameSanitizer.toSafeFileName(file.getOriginalFilename());
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read profile picture bytes: {}", file.getOriginalFilename(), e);
            throw new StorageServiceException("No se pudo leer la imagen enviada.", e);
        }

        try {
            restClient.post()
                    .uri(uploadUrl)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", file.getContentType())
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Profile picture upload failed - bucket: {}, path: {}, status: {}, body: {}",
                    bucket, path, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
        } catch (ResourceAccessException e) {
            log.error("Profile picture upload unreachable - bucket: {}, path: {}", bucket, path, e);
            throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
        }

        log.info("Profile picture uploaded to Supabase Storage: {}/{}", bucket, path);
        return path;
    }

    @Override
    public String generatePublicUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    @Override
    public void delete(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;

        try {
            restClient.delete()
                    .uri(deleteUrl)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Profile picture deleted from Supabase Storage: {}/{}", bucket, path);
        } catch (RestClientResponseException e) {
            if (isObjectNotFound(e)) {
                log.warn("Profile picture delete skipped, object already absent - bucket: {}, path: {}",
                        bucket, path);
                return;
            }
            log.warn("Profile picture delete failed - bucket: {}, path: {}, status: {}", bucket, path,
                    e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            log.warn("Profile picture delete unreachable - bucket: {}, path: {}", bucket, path, e);
        }
    }

    @Override
    public void deleteByUserId(UUID userId) {
        String prefix = userId + "/";
        String listUrl = supabaseUrl + "/storage/v1/object/list/" + bucket;

        try {
            Object[] response = restClient.post()
                    .uri(listUrl)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(new ListRequest(prefix, 100, 0))
                    .retrieve()
                    .body(Object[].class);

            if (response != null) {
                for (Object obj : response) {
                    if (obj instanceof java.util.Map<?, ?> map) {
                        String name = (String) map.get("name");
                        if (name != null) {
                            delete(prefix + name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list/delete profile pictures for user: {}", userId, e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("El archivo de imagen es requerido.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("La imagen no puede superar los 5MB.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new FormatNotAllowedException("Formato no válido. Se aceptan JPG, PNG y WebP.");
        }
    }

    /**
     * Supabase Storage returns 404 (or a 400 with a "not found" body depending on the version)
     * when the object is already absent. Deleting is idempotent, so both cases are treated as success.
     */
    private boolean isObjectNotFound(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 404) {
            return true;
        }
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        String body = exception.getResponseBodyAsString();
        if (body == null) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("not found") || normalized.contains("not_found");
    }

    private record ListRequest(String prefix, int limit, int offset) {}
}