package com.knowlink.api.resources.service.interfaces;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ISupabaseStorageService {
    String upload(MultipartFile file, UUID subjectId);
    String upload(MultipartFile file, String bucket, String folder);
    String generateSignedUrl(String path, int expirationSeconds);
    String generateSignedUrl(String bucket, String path, int expirationSeconds);
    void delete(String path);
    void delete(String bucket, String path);
}