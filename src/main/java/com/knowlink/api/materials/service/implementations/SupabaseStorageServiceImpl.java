package com.knowlink.api.materials.service.implementations;

import com.knowlink.api.materials.exception.StorageServiceException;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
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
import java.util.UUID;

@Service
@Slf4j
public class SupabaseStorageServiceImpl implements ISupabaseStorageService {

    private static final String STORAGE_ERROR_MESSAGE =
            "No se pudo comunicar con el almacenamiento de archivos. Intentá nuevamente más tarde.";

    @Value("${supabase.storage.materials-bucket}")
    private String materialsBucket;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    private final RestClient restClient;

    public SupabaseStorageServiceImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String upload(MultipartFile file, UUID subjectId) {
        return upload(file, materialsBucket, subjectId.toString());
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        return upload(file, materialsBucket, folder);
    }

    @Override
    public String upload(MultipartFile file, String bucket, String folder) {
        String path = folder + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("No se pudieron leer los bytes del archivo {}", file.getOriginalFilename(), e);
            throw new StorageServiceException("No se pudo leer el archivo enviado.", e);
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
            log.error("Supabase upload failed - bucket: {}, path: {}, status: {}, body: {}",
                    bucket, path, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
        } catch (ResourceAccessException e) {
            log.error("Supabase upload unreachable - bucket: {}, path: {}", bucket, path, e);
            throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
        }

        log.info("File uploaded to Supabase Storage: {}/{}", bucket, path);
        return path;
    }

    @Override
    public String generateSignedUrl(String path, int expirationSeconds) {
        return generateSignedUrl(materialsBucket, path, expirationSeconds);
    }

    @Override
    public String generateSignedUrl(String bucket, String path, int expirationSeconds) {
        String signUrl = supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + path;

        try {
            SignedUrlResponse response = restClient.post()
                    .uri(signUrl)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", expirationSeconds))
                    .retrieve()
                    .body(SignedUrlResponse.class);

            return supabaseUrl + "/storage/v1" + response.signedURL();
        } catch (RestClientResponseException e) {
            log.error("Supabase signed URL failed - bucket: {}, path: {}, status: {}, body: {}",
                    bucket, path, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
        } catch (ResourceAccessException e) {
            log.error("Supabase signed URL unreachable - bucket: {}, path: {}", bucket, path, e);
            throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
        }
    }

    @Override
    public void delete(String path) {
        delete(materialsBucket, path);
    }

    @Override
    public void delete(String bucket, String path) {
        String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;

        try {
            restClient.delete()
                    .uri(deleteUrl)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (isObjectNotFound(e)) {
                log.warn("Supabase delete skipped, object already absent - bucket: {}, path: {}",
                        bucket, path);
                return;
            }
            log.error("Supabase delete failed - bucket: {}, path: {}, status: {}, body: {}",
                    bucket, path, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
        } catch (ResourceAccessException e) {
            log.error("Supabase delete unreachable - bucket: {}, path: {}", bucket, path, e);
            throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
        }

        log.info("File deleted from Supabase Storage: {}/{}", bucket, path);
    }

    /**
     * Supabase Storage returns 404 (or a 400 with a "not found" body depending on the
     * version) when the object is already absent. Deleting is idempotent, so both cases
     * are treated as success.
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

    private record SignedUrlResponse(String signedURL) {}
}
