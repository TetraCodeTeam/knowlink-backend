package com.knowlink.api.recursos.service.interfaces;

import com.knowlink.api.recursos.dto.MaterialResponse;
import com.knowlink.api.recursos.dto.MaterialUploadRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IMaterialService {
    MaterialResponse upload(MaterialUploadRequest request, MultipartFile file, UUID tutorUserId);
    List<MaterialResponse> listBySubject(UUID subjectId, UUID userId, String role);
    String getDownloadUrl(UUID materialId, UUID userId, String role);
}
