package com.knowlink.api.materials.service.interfaces;

import com.knowlink.api.materials.controller.responses.MaterialResponse;

import java.util.List;
import java.util.UUID;

public interface IMaterialAccessService {
    boolean hasAccess(UUID studentId, UUID tutorUserId);

    List<MaterialResponse> listAccessibleMaterials(UUID studentId, UUID tutorUserId, UUID subjectId);

    void validateAccessOrDeny(UUID studentId, UUID materialId);
}
