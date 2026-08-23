package com.knowlink.api.recursos.service.interfaces;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ISupabaseStorageService {
    String upload(MultipartFile file, UUID subjectId);
    String generateSignedUrl(String path, int expirationSeconds);
    void delete(String path);
}
