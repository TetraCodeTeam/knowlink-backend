package com.knowlink.api.resources.service.implementations;

import com.knowlink.api.resources.service.interfaces.ISupabaseStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SupabaseStorageServiceImpl implements ISupabaseStorageService {

    private static final String BUCKET = "materials";

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
        String path = subjectId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
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

        log.info("File uploaded to Supabase Storage: {}", path);
        return path;
    }

    @Override
    public String generateSignedUrl(String path, int expirationSeconds) {
        String signUrl = supabaseUrl + "/storage/v1/object/sign/" + BUCKET + "/" + path;

        SignedUrlResponse response = restClient.post()
                .uri(signUrl)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", expirationSeconds))
                .retrieve()
                .body(SignedUrlResponse.class);

        return supabaseUrl + "/storage/v1" + response.signedURL();
    }

    @Override
    public void delete(String path) {
        String deleteUrl = supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + path;

        restClient.delete()
                .uri(deleteUrl)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .retrieve()
                .toBodilessEntity();

        log.info("File deleted from Supabase Storage: {}", path);
    }

    private record SignedUrlResponse(String signedURL) {}
}
