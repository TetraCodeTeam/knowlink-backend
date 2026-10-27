package com.knowlink.api.students.support.services.implementations;

import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.data.models.SessionClaimComment;
import com.knowlink.api.claims.repositories.SessionClaimCommentRepository;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.claims.services.interfaces.IClaimSupportDetailsService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimResponse;
import com.knowlink.api.students.support.data.mappers.StudentSupportMapper;
import com.knowlink.api.students.support.services.interfaces.IStudentSessionSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentSessionSupportService implements IStudentSessionSupportService {

    private final SessionClaimRepository sessionClaimRepository;
    private final SessionClaimCommentRepository claimCommentRepository;
    private final INotificationService notificationService;
    private final IClaimSupportDetailsService claimSupportDetailsService;
    private final StudentSupportMapper studentSupportMapper;

    @Override
    @Transactional(readOnly = true)
    public List<StudentSessionClaimResponse> getClaims(UUID studentUserId) {
        return sessionClaimRepository.findStudentClaims(studentUserId).stream()
                .map(studentSupportMapper::toSessionClaimResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentSessionClaimDetailResponse getClaimDetails(UUID studentUserId, UUID claimId) {
        SessionClaim claim = sessionClaimRepository.findStudentClaimDetails(claimId, studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CLAIM_NOT_FOUND",
                        "El reclamo no existe.",
                        "Session claim " + claimId + " not found for student " + studentUserId));

        var booking = claim.getBooking();
        List<ClaimAttachmentUrlResponse> attachments = claimSupportDetailsService.getAttachments(claimId);
        List<ClaimCommentResponse> comments = claimSupportDetailsService.getComments(claimId);
        return studentSupportMapper.toSessionClaimDetails(
                claim,
                claimSupportDetailsService.getFundsSummary(booking, claim.getStatus()),
                claimSupportDetailsService.getReviewProgress(claim),
                attachments,
                comments);
    }

    @Override
    @Transactional
    public ClaimCommentResponse addClaimComment(UUID studentUserId, UUID claimId, AddClaimCommentRequest request) {
        SessionClaim claim = sessionClaimRepository.findStudentClaimDetails(claimId, studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CLAIM_NOT_FOUND",
                        "El reclamo no existe.",
                        "Session claim " + claimId + " not found for student " + studentUserId));
        if (claim.getStatus() == ClaimStatus.RESOLVED) {
            throw new DuplicateResourceException(
                    "CLAIM_ALREADY_RESOLVED",
                    "No podés comentar un reclamo que ya fue resuelto.",
                    "Comment attempted on resolved claim " + claimId);
        }

        var booking = claim.getBooking();
        SessionClaimComment comment = claimCommentRepository.save(SessionClaimComment.builder()
                .claim(claim)
                .author(booking.getStudent())
                .message(request.comment())
                .build());
        notificationService.createSupportNotification(
                booking.getTutor().getUserId(),
                Role.TUTOR,
                NotificationType.SESSION_CLAIM_COMMENT,
                claim.getClaimId(),
                "Nuevo comentario en un reclamo",
                "El alumno agregó un comentario al reclamo de la sesión de "
                        + booking.getTutorSubject().getSubject().getName() + ".");
        return claimSupportDetailsService.toCommentResponse(comment);
    }
}
