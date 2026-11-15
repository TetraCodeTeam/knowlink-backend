package com.knowlink.api.materials.service.interfaces;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IProfileImageService {
    String upload(MultipartFile file, UUID userId);

    /**
     * Builds the permanent public URL of an object stored in the profile photos bucket.
     * Unlike a signed URL it never expires, but it requires the bucket to be public.
     * Returns null when the path is empty.
     */
    String generatePublicUrl(String path);

    void delete(String path);

    void deleteByUserId(UUID userId);
}