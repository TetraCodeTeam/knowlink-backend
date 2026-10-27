package com.knowlink.api.tutors.support.services.interfaces;

import com.knowlink.api.tutors.support.controllers.requests.SubmitMaterialDefenseRequest;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialResponse;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ITutorMaterialSupportService {

    List<TutorReportedMaterialResponse> getReportedMaterials(UUID tutorUserId);

    MaterialReportingPolicyResponse getMaterialReportingPolicy();

    TutorReportedMaterialDetailResponse getReportedMaterialDetails(UUID tutorUserId, UUID materialId);

    TutorReportedMaterialDetailResponse getAdminReportedMaterialDetails(UUID materialId);

    TutorReportedMaterialDetailResponse submitMaterialDefense(
            UUID tutorUserId,
            UUID materialId,
            SubmitMaterialDefenseRequest request,
            List<MultipartFile> files);
}
