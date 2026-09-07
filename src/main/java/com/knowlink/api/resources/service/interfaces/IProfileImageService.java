package com.knowlink.api.resources.service.interfaces;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IProfileImageService {
    String upload(MultipartFile file, UUID userId);
    void delete(String path);
    void deleteByUserId(UUID userId);
}
