package com.knowlink.api.students.support.services.implementations;

import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSupportResponse;
import com.knowlink.api.students.support.data.mappers.StudentSupportMapper;
import com.knowlink.api.students.support.services.interfaces.IStudentMaterialSupportService;
import com.knowlink.api.students.support.services.interfaces.IStudentSessionSupportService;
import com.knowlink.api.students.support.services.interfaces.IStudentSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentSupportServiceImpl implements IStudentSupportService {

    private final IStudentSessionSupportService sessionSupportService;
    private final IStudentMaterialSupportService materialSupportService;
    private final INotificationService notificationService;
    private final StudentSupportMapper studentSupportMapper;

    @Override
    @Transactional
    public StudentSupportResponse getSupport(UUID studentUserId) {
        var claims = sessionSupportService.getClaims(studentUserId);
        var reports = materialSupportService.getReports(studentUserId);
        notificationService.markSupportAsRead(studentUserId);
        return new StudentSupportResponse(
                studentSupportMapper.toSummary(claims, reports),
                claims,
                reports);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialReportingPolicyResponse getMaterialReportingPolicy() {
        return materialSupportService.getMaterialReportingPolicy();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentSessionClaimDetailResponse getSessionClaimDetails(UUID studentUserId, UUID claimId) {
        return sessionSupportService.getClaimDetails(studentUserId, claimId);
    }

    @Override
    @Transactional
    public ClaimCommentResponse addClaimComment(UUID studentUserId, UUID claimId, AddClaimCommentRequest request) {
        return sessionSupportService.addClaimComment(studentUserId, claimId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentMaterialReportDetailResponse getMaterialReportDetails(UUID studentUserId, UUID reportId) {
        return materialSupportService.getReportDetails(studentUserId, reportId);
    }
}
