package com.knowlink.api.admin.materialreports.controllers.responses;

import com.knowlink.api.materials.data.enums.MaterialType;

import java.time.LocalDateTime;

public record MaterialInfoResponse(
        String fileName,
        MaterialType materialType,
        Long sizeInBytes,
        LocalDateTime uploadedAt
) {
}