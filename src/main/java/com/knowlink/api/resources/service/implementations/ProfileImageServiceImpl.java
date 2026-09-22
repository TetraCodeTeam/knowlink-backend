package com.knowlink.api.resources.service.implementations;

import com.knowlink.api.resources.service.interfaces.IProfileImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ProfileImageServiceImpl implements IProfileImageService {

    private static final String BUCKET = "profilePhotos";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

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

        String path = userId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + path;

        try {
            restClient.post()
                    .uri(uploadUrl)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", file.getContentType())
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file bytes", e);
        }

        log.info("Profile image uploaded to Supabase Storage: {}", path);
        return path;
    }

    @Override
    public String generateSignedUrl(String path, int expirationSeconds) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String signUrl = supabaseUrl + "/storage/v1/object/sign/" + BUCKET + "/" + path;

        SignedUrlResponse response = restClient.post()
                .uri(signUrl)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", expirationSeconds))
                .retrieve()
                .body(SignedUrlResponse.class);

        return supabaseUrl + "/storage/v1" + response.signedURL();
    }

    @Override
    public void delete(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String deleteUrl = supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + path;

        try {
            restClient.delete()
                    .uri(deleteUrl)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Profile image deleted from Supabase Storage: {}", path);
        } catch (Exception e) {
            log.warn("Failed to delete profile image from Supabase: {}", path, e);
        }
    }

    @Override
    public void deleteByUserId(UUID userId) {
        String prefix = userId + "/";
        String listUrl = supabaseUrl + "/storage/v1/object/list/" + BUCKET;

        try {
            Object[] response = restClient.post()
                    .uri(listUrl)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", "application/json")
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
            log.warn("Failed to list/delete profile images for user: {}", userId, e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen es requerido.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen no puede superar los 5MB.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Formato no válido. Se aceptan JPG, PNG y WebP.");
        }
    }

    private record ListRequest(String prefix, int limit, int offset) {}

    private record SignedUrlResponse(String signedURL) {}
}
