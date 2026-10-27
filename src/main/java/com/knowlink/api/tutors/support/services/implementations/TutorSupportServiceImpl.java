package com.knowlink.api.tutors.support.services.implementations;

import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.tutors.support.controllers.requests.SubmitMaterialDefenseRequest;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportSummaryResponse;
import com.knowlink.api.tutors.support.data.mappers.TutorSupportMapper;
import com.knowlink.api.tutors.support.services.interfaces.ITutorMaterialSupportService;
import com.knowlink.api.tutors.support.services.interfaces.ITutorSessionSupportService;
import com.knowlink.api.tutors.support.services.interfaces.ITutorSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TutorSupportServiceImpl implements ITutorSupportService {

    private final ITutorSessionSupportService sessionSupportService;
    private final ITutorMaterialSupportService materialSupportService;
    private final INotificationService notificationService;
    private final TutorSupportMapper tutorSupportMapper;

    @Override
    @Transactional
    public TutorSupportResponse getSupport(UUID tutorUserId) {
        var claims = sessionSupportService.getClaims(tutorUserId);
        var reportedMaterials = materialSupportService.getReportedMaterials(tutorUserId);
        notificationService.markSupportAsRead(tutorUserId, Role.TUTOR);
        TutorSupportSummaryResponse summary = tutorSupportMapper.toSummary(claims, reportedMaterials);
        return new TutorSupportResponse(summary, claims, reportedMaterials);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialReportingPolicyResponse getMaterialReportingPolicy() {
        return materialSupportService.getMaterialReportingPolicy();
    }

    @Override
    @Transactional(readOnly = true)
    public TutorSupportClaimDetailResponse getClaimDetails(UUID tutorUserId, UUID claimId) {
        return sessionSupportService.getClaimDetails(tutorUserId, claimId);
    }

    @Override
    @Transactional
    public ClaimCommentResponse addClaimComment(
            UUID tutorUserId,
            UUID claimId,
            AddClaimCommentRequest request) {
        return sessionSupportService.addClaimComment(tutorUserId, claimId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public TutorReportedMaterialDetailResponse getReportedMaterialDetails(UUID tutorUserId, UUID materialId) {
        return materialSupportService.getReportedMaterialDetails(tutorUserId, materialId);
    }

    @Override
    @Transactional(readOnly = true)
    public TutorReportedMaterialDetailResponse getAdminReportedMaterialDetails(UUID materialId) {
        return materialSupportService.getAdminReportedMaterialDetails(materialId);
    }

    @Override
    @Transactional
    public TutorReportedMaterialDetailResponse submitMaterialDefense(
            UUID tutorUserId,
            UUID materialId,
            SubmitMaterialDefenseRequest request,
            List<MultipartFile> files) {
        return materialSupportService.submitMaterialDefense(tutorUserId, materialId, request, files);
    }
}
